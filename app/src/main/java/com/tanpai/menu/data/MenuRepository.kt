package com.tanpai.menu.data

import androidx.room.withTransaction
import com.tanpai.menu.data.db.AppMeta
import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.data.db.SaleRecord
import com.tanpai.menu.data.db.TpDatabase
import com.tanpai.menu.domain.SellError
import com.tanpai.menu.domain.SellPolicy
import com.tanpai.menu.domain.SellResult
import com.tanpai.menu.domain.UnsellPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MenuSnapshot(
    val categories: List<Category> = emptyList(),
    val dishes: List<Dish> = emptyList()
)

data class FullMenu(
    val categories: List<Category> = emptyList(),
    val dishes: List<Dish> = emptyList()
)

/**
 * 菜单仓储：主机唯一数据源。
 *
 * 全部写方法经 [writeMutex] 串行化，事务提交后按提交顺序向 [events] 发出 [MenuChange]；
 * 回滚不发事件，保证「已广播的必然已落库」（`docs/design/0002` 第 6.2 节）。
 * 订阅方必须快速消费，缓冲（64）用尽时写操作会在 [events] 上挂起。
 */
class MenuRepository(
    private val db: TpDatabase,
    private val now: () -> Long = System::currentTimeMillis
) {

    private val categoryDao = db.categoryDao()
    private val dishDao = db.dishDao()
    private val saleRecordDao = db.saleRecordDao()
    private val appMetaDao = db.appMetaDao()

    private val writeMutex = Mutex()
    private val mutableEvents = MutableSharedFlow<MenuChange>(extraBufferCapacity = EVENT_BUFFER)

    /** 写事务提交后的变更事件流，供主机广播器订阅。 */
    val events: SharedFlow<MenuChange> = mutableEvents.asSharedFlow()

    fun observeMenu(): Flow<MenuSnapshot> =
        combine(categoryDao.observeEnabled(), dishDao.observeEnabled()) { categories, dishes ->
            MenuSnapshot(categories = categories, dishes = dishes)
        }

    suspend fun currentVersion(): Long =
        appMetaDao.get(VERSION_KEY)?.value?.toLongOrNull() ?: 0L

    suspend fun allImagePaths(): Set<String> = dishDao.allImagePaths().toSet()

    suspend fun exportAll(): FullMenu = FullMenu(categoryDao.getAll(), dishDao.getAll())

    suspend fun replaceAll(categories: List<Category>, dishes: List<Dish>): Unit = mutate {
        saleRecordDao.deleteAll()
        dishDao.deleteAll()
        categoryDao.deleteAll()
        categories.forEach { categoryDao.insert(it) }
        dishes.forEach { dishDao.insert(it) }
        Unit to MenuChange.FullReload(bumpVersion())
    }

    suspend fun seedIfEmpty() {
        if (categoryDao.count() > 0) return
        mutate<Unit> {
            if (categoryDao.count() > 0) return@mutate Unit to null
            insertSeed()
            Unit to MenuChange.FullReload(bumpVersion())
        }
    }

    /** 清空菜单并写回默认菜单（设置页「恢复默认菜单」）。图片清理由调用方负责。 */
    suspend fun resetToSeedMenu(): Unit = mutate<Unit> {
        saleRecordDao.deleteAll()
        dishDao.deleteAll()
        categoryDao.deleteAll()
        insertSeed()
        Unit to MenuChange.FullReload(bumpVersion())
    }

    private suspend fun insertSeed() {
        SeedMenu.categories.forEach { seed ->
            val categoryId = categoryDao.insert(seed.category.copy(updatedAt = now()))
            seed.dishes.forEach { dish ->
                dishDao.insert(dish.copy(categoryId = categoryId, updatedAt = now()))
            }
        }
    }

    suspend fun createCategory(name: String, sortOrder: Int): Long {
        val fresh = Category(name = name, sortOrder = sortOrder, updatedAt = now())
        return mutate {
            val id = categoryDao.insert(fresh)
            id to MenuChange.CategoryUpdated(bumpVersion(), fresh.copy(id = id))
        }
    }

    suspend fun updateCategory(id: Long, name: String, sortOrder: Int) {
        mutate<Category?> {
            val current = categoryDao.getById(id)
            if (current == null) {
                null to null
            } else {
                val entity = current.copy(name = name, sortOrder = sortOrder, updatedAt = now())
                categoryDao.update(entity)
                entity to MenuChange.CategoryUpdated(bumpVersion(), entity)
            }
        }
    }

    suspend fun deleteCategory(id: Long): Unit = mutate<Unit> {
        val timestamp = now()
        val dishIds = dishDao.idsEnabledInCategory(id)
        categoryDao.softDelete(id, timestamp)
        dishDao.softDeleteByCategory(id, timestamp)
        Unit to MenuChange.CategoryDeleted(bumpVersion(), id, dishIds)
    }

    suspend fun createDish(dish: Dish): Long {
        val fresh = dish.copy(updatedAt = now())
        return mutate {
            val id = dishDao.insert(fresh)
            id to MenuChange.DishUpdated(bumpVersion(), fresh.copy(id = id))
        }
    }

    suspend fun updateDish(dish: Dish): Unit = mutate<Unit> {
        val fresh = dish.copy(updatedAt = now())
        dishDao.update(fresh)
        Unit to MenuChange.DishUpdated(bumpVersion(), fresh)
    }

    suspend fun deleteDish(id: Long): Unit = mutate<Unit> {
        dishDao.softDelete(id, now())
        Unit to MenuChange.DishDeleted(bumpVersion(), id)
    }

    suspend fun sell(dishId: Long, deviceId: String): SellResult = mutate {
        val dish = dishDao.getById(dishId)
            ?: return@mutate SellResult.Failure(SellError.NotFound) to null
        when (val result = SellPolicy.applySell(dish, now())) {
            is SellResult.Failure -> result to null
            is SellResult.Success -> {
                dishDao.update(result.dish)
                saleRecordDao.insert(
                    SaleRecord(dishId = dishId, deviceId = deviceId, createdAt = now())
                )
                result to MenuChange.DishUpdated(bumpVersion(), result.dish)
            }
        }
    }

    suspend fun unsell(dishId: Long, deviceId: String): SellResult = mutate {
        val dish = dishDao.getById(dishId)
            ?: return@mutate SellResult.Failure(SellError.NotFound) to null
        val record = UnsellPolicy.pickLastRevocable(
            saleRecordDao.getActiveFor(dishId, deviceId),
            deviceId
        ) ?: return@mutate SellResult.Failure(SellError.NoRevocableRecord) to null
        saleRecordDao.markRevoked(record.id)
        val updated = UnsellPolicy.applyUnsell(dish, now())
        dishDao.update(updated)
        SellResult.Success(updated) to MenuChange.DishUpdated(bumpVersion(), updated)
    }

    /**
     * 单写者执行：串行化 → 事务 → 提交后广播。
     *
     * [block] 在事务内执行，返回「业务结果 to 变更事件」；无变更时事件传 null。
     */
    private suspend fun <T> mutate(block: suspend () -> Pair<T, MenuChange?>): T =
        writeMutex.withLock {
            val (value, change) = db.withTransaction { block() }
            if (change != null) mutableEvents.emit(change)
            value
        }

    private suspend fun bumpVersion(): Long {
        val next = currentVersion() + 1
        appMetaDao.upsert(AppMeta(VERSION_KEY, next.toString()))
        return next
    }

    companion object {
        const val VERSION_KEY = "version"

        /** 事件缓冲容量；订阅方正常消费时不会触顶。 */
        private const val EVENT_BUFFER = 64
    }
}