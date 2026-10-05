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
import kotlinx.coroutines.flow.combine

data class MenuSnapshot(
    val categories: List<Category> = emptyList(),
    val dishes: List<Dish> = emptyList()
)

data class FullMenu(
    val categories: List<Category> = emptyList(),
    val dishes: List<Dish> = emptyList()
)

class MenuRepository(
    private val db: TpDatabase,
    private val now: () -> Long = System::currentTimeMillis
) {

    private val categoryDao = db.categoryDao()
    private val dishDao = db.dishDao()
    private val saleRecordDao = db.saleRecordDao()
    private val appMetaDao = db.appMetaDao()

    fun observeMenu(): Flow<MenuSnapshot> =
        combine(categoryDao.observeEnabled(), dishDao.observeEnabled()) { categories, dishes ->
            MenuSnapshot(categories = categories, dishes = dishes)
        }

    suspend fun currentVersion(): Long =
        appMetaDao.get(VERSION_KEY)?.value?.toLongOrNull() ?: 0L

    suspend fun allImagePaths(): Set<String> = dishDao.allImagePaths().toSet()

    suspend fun exportAll(): FullMenu = FullMenu(categoryDao.getAll(), dishDao.getAll())

    suspend fun replaceAll(categories: List<Category>, dishes: List<Dish>) {
        db.withTransaction {
            saleRecordDao.deleteAll()
            dishDao.deleteAll()
            categoryDao.deleteAll()
            categories.forEach { categoryDao.insert(it) }
            dishes.forEach { dishDao.insert(it) }
            bumpVersion()
        }
    }

    suspend fun seedIfEmpty() {
        if (categoryDao.count() > 0) return
        db.withTransaction {
            SeedMenu.categories.forEach { seed ->
                val categoryId = categoryDao.insert(seed.category.copy(updatedAt = now()))
                seed.dishes.forEach { dish ->
                    dishDao.insert(dish.copy(categoryId = categoryId, updatedAt = now()))
                }
            }
            bumpVersion()
        }
    }

    suspend fun createCategory(name: String, sortOrder: Int): Long =
        db.withTransaction {
            val id = categoryDao.insert(Category(name = name, sortOrder = sortOrder, updatedAt = now()))
            bumpVersion()
            id
        }

    suspend fun updateCategory(id: Long, name: String, sortOrder: Int) {
        db.withTransaction {
            val current = categoryDao.getById(id) ?: return@withTransaction
            categoryDao.update(current.copy(name = name, sortOrder = sortOrder, updatedAt = now()))
            bumpVersion()
        }
    }

    suspend fun deleteCategory(id: Long) {
        db.withTransaction {
            val timestamp = now()
            categoryDao.softDelete(id, timestamp)
            dishDao.softDeleteByCategory(id, timestamp)
            bumpVersion()
        }
    }

    suspend fun createDish(dish: Dish): Long =
        db.withTransaction {
            val id = dishDao.insert(dish.copy(updatedAt = now()))
            bumpVersion()
            id
        }

    suspend fun updateDish(dish: Dish) {
        db.withTransaction {
            dishDao.update(dish.copy(updatedAt = now()))
            bumpVersion()
        }
    }

    suspend fun deleteDish(id: Long) {
        db.withTransaction {
            dishDao.softDelete(id, now())
            bumpVersion()
        }
    }

    suspend fun sell(dishId: Long, deviceId: String): SellResult =
        db.withTransaction {
            val dish = dishDao.getById(dishId)
                ?: return@withTransaction SellResult.Failure(SellError.NotFound)
            when (val result = SellPolicy.applySell(dish, now())) {
                is SellResult.Failure -> result
                is SellResult.Success -> {
                    dishDao.update(result.dish)
                    saleRecordDao.insert(
                        SaleRecord(dishId = dishId, deviceId = deviceId, createdAt = now())
                    )
                    bumpVersion()
                    result
                }
            }
        }

    suspend fun unsell(dishId: Long, deviceId: String): SellResult =
        db.withTransaction {
            val dish = dishDao.getById(dishId)
                ?: return@withTransaction SellResult.Failure(SellError.NotFound)
            val record = UnsellPolicy.pickLastRevocable(
                saleRecordDao.getActiveFor(dishId, deviceId),
                deviceId
            ) ?: return@withTransaction SellResult.Failure(SellError.NoRevocableRecord)
            saleRecordDao.markRevoked(record.id)
            val updated = UnsellPolicy.applyUnsell(dish, now())
            dishDao.update(updated)
            bumpVersion()
            SellResult.Success(updated)
        }

    private suspend fun bumpVersion() {
        appMetaDao.upsert(AppMeta(VERSION_KEY, (currentVersion() + 1).toString()))
    }

    companion object {
        const val VERSION_KEY = "version"
    }
}
