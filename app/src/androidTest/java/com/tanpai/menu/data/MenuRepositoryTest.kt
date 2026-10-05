package com.tanpai.menu.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.data.db.TpDatabase
import com.tanpai.menu.domain.SellError
import com.tanpai.menu.domain.SellResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenuRepositoryTest {

    private lateinit var db: TpDatabase
    private lateinit var repository: MenuRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TpDatabase::class.java
        ).build()
        repository = MenuRepository(db) { 1000L }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newDish(stockCount: Int = 5) = Dish(
        categoryId = 1L, name = "烤串", priceCents = 300, stockEnabled = true,
        stockCount = stockCount, updatedAt = 0L
    )

    @Test
    fun seedInsertsSampleMenuOnlyOnce() = runTest {
        repository.seedIfEmpty()
        repository.seedIfEmpty()
        assertEquals(2, db.categoryDao().getAll().size)
    }

    @Test
    fun versionIncrementsOncePerWrite() = runTest {
        assertEquals(0L, repository.currentVersion())
        repository.createCategory("热菜", 1)
        assertEquals(1L, repository.currentVersion())
        repository.createCategory("饮品", 2)
        assertEquals(2L, repository.currentVersion())
    }

    @Test
    fun sellDecrementsStockAndWritesRecord() = runTest {
        val id = repository.createDish(newDish(stockCount = 3))
        val result = repository.sell(id, deviceId = "A")
        assertTrue(result is SellResult.Success)
        assertEquals(2, db.dishDao().getById(id)?.stockCount)
        assertEquals(1, db.saleRecordDao().getActiveFor(id, "A").size)
    }

    @Test
    fun sellToZeroMarksSoldOutThenRejects() = runTest {
        val id = repository.createDish(newDish(stockCount = 1))
        repository.sell(id, deviceId = "A")
        assertTrue(db.dishDao().getById(id)?.soldOut == true)
        val second = repository.sell(id, deviceId = "A")
        assertEquals(SellResult.Failure(SellError.SoldOut), second)
    }

    @Test
    fun unsellRestoresStockAndClearsSoldOut() = runTest {
        val id = repository.createDish(newDish(stockCount = 1))
        repository.sell(id, deviceId = "A")
        val result = repository.unsell(id, deviceId = "A")
        assertTrue(result is SellResult.Success)
        assertEquals(1, db.dishDao().getById(id)?.stockCount)
        assertEquals(false, db.dishDao().getById(id)?.soldOut)
    }

    @Test
    fun unsellWithoutRecordFails() = runTest {
        val id = repository.createDish(newDish())
        val result = repository.unsell(id, deviceId = "A")
        assertEquals(SellResult.Failure(SellError.NoRevocableRecord), result)
    }

    @Test
    fun deletingCategoryHidesItsDishes() = runTest {
        val categoryId = repository.createCategory("热菜", 1)
        repository.createDish(newDish().copy(categoryId = categoryId))
        repository.deleteCategory(categoryId)
        val snapshot = repository.observeMenu().first()
        assertTrue(snapshot.categories.isEmpty())
        assertTrue(snapshot.dishes.isEmpty())
    }
}
