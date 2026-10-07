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
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * 种子图提供者：入参为 `assets/dishes/` 下的文件名，返回相对图片路径或 null（无图）。
     * 默认不提供，保持数据层不依赖 Android Context，由 `AppContainer` 注入 `ImageStore`。
     */
    private val seedImage: suspend (String) -> String? = { null }
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

    /**
     * 广播一次展示设置变更（`docs/design/0023` 第 2.2 节）。
     *
     * 展示设置存在 `SettingsStore` 而非 Room，因此没有写事务；调用方在落盘后调用本方法，
     * 由主机广播器把它变成 `display.updated` 帧。`version` 沿用当前全局版本，不做 +1。
     */
    suspend fun notifyDisplayChanged() {
        mutableEvents.emit(MenuChange.DisplayUpdated(currentVersion()))
    }

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
        val imagePaths = importSeedImages()
        mutate<Unit> {
            if (categoryDao.count() > 0) return@mutate Unit to null
            insertSeed(imagePaths)
            Unit to MenuChange.FullReload(bumpVersion())
        }
    }

    /** 清空菜单并写回默认菜单（设置页「恢复默认菜单」）。旧图片清理由调用方负责。 */
    suspend fun resetToSeedMenu() {
        val imagePaths = importSeedImages()
        mutate<Unit> {
            saleRecordDao.deleteAll()
            dishDao.deleteAll()
            categoryDao.deleteAll()
            insertSeed(imagePaths)
            Unit to MenuChange.FullReload(bumpVersion())
        }
    }

    /**
     * 事务外导入种子图，返回「菜品名 -> 图片路径」。
     * 单张导入失败（返回 null）时该菜无图，展示层回退首字占位，不影响种子写入。
     */
    private suspend fun importSeedImages(): Map<String, String> =
        SeedMenu.imageAssetByName.mapNotNull { (dishName, asset) ->
            seedImage(asset)?.let { dishName to it }
        }.toMap()

    private suspend fun insertSeed(imagePaths: Map<String, String>) {
        SeedMenu.categories.forEach { seed ->
            val categoryId = categoryDao.insert(seed.category.copy(updatedAt = now()))
            seed.dishes.forEach { dish ->
                dishDao.insert(
                    dish.copy(
                        categoryId = categoryId,
                        imagePath = imagePaths[dish.name] ?: dish.imagePath,
                        updatedAt = now()
                    )
                )
            }
        }
    }

    /** 读取启用中的分类（软删除不可见），供 REST 路径复用。 */
    suspend fun categoryById(id: Long): Category? =
        categoryDao.getById(id)?.takeIf { it.enabled }

    /** 读取启用中的菜品（软删除不可见），供 REST 路径复用。 */
    suspend fun dishById(id: Long): Dish? =
        dishDao.getById(id)?.takeIf { it.enabled }

    /** 启用中的分类 / 菜品列表（REST 列表与 `/state` 快照共用语义）。 */
    suspend fun enabledCategories(): List<Category> = categoryDao.getAll().filter { it.enabled }

    suspend fun enabledDishes(): List<Dish> = dishDao.getAll().filter { it.enabled }

    /** 以实体整体更新分类（REST PATCH 用），走单写者事务并广播事件。 */
    suspend fun saveCategory(category: Category): Category {
        val fresh = category.copy(updatedAt = now())
        return mutate {
            categoryDao.update(fresh)
            fresh to MenuChange.CategoryUpdated(bumpVersion(), fresh)
        }
    }

    /** 以实体整体更新菜品（REST PATCH 用），走单写者事务并广播事件。 */
    suspend fun saveDish(dish: Dish): Dish {
        val fresh = dish.copy(updatedAt = now())
        return mutate {
            dishDao.update(fresh)
            fresh to MenuChange.DishUpdated(bumpVersion(), fresh)
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

    /**
     * 撤销该菜品最后一条卖出记录。
     *
     * 不按设备归属限制：手机与平板可互相撤销（`docs/design/0020` 第 2.5 节）。
     * [deviceId] 保留在签名中，继续作为卖出流水的归属信息。
     */
    suspend fun unsell(dishId: Long, deviceId: String): SellResult = mutate {
        val dish = dishDao.getById(dishId)
            ?: return@mutate SellResult.Failure(SellError.NotFound) to null
        val record = UnsellPolicy.pickLastRevocable(saleRecordDao.getActiveFor(dishId))
            ?: return@mutate SellResult.Failure(SellError.NoRevocableRecord) to null
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
