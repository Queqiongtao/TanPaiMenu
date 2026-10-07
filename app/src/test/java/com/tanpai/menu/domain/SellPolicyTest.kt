package com.tanpai.menu.domain

import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.data.db.SaleRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SellPolicyTest {

    private fun dish(
        stockEnabled: Boolean = true,
        stockCount: Int = 5,
        soldOut: Boolean = false,
        enabled: Boolean = true
    ) = Dish(
        id = 1L,
        categoryId = 1L,
        name = "烤串",
        priceCents = 300,
        stockEnabled = stockEnabled,
        stockCount = stockCount,
        soldOut = soldOut,
        enabled = enabled,
        updatedAt = 0L
    )

    @Test
    fun sellsOneAndUpdatesTimestamp() {
        val result = SellPolicy.applySell(dish(stockCount = 5), now = 100L)
        assertTrue(result is SellResult.Success)
        val updated = (result as SellResult.Success).dish
        assertEquals(4, updated.stockCount)
        assertEquals(100L, updated.updatedAt)
        assertEquals(false, updated.soldOut)
    }

    @Test
    fun autoSoldOutWhenReachingZero() {
        val result = SellPolicy.applySell(dish(stockCount = 1), now = 1L)
        val updated = (result as SellResult.Success).dish
        assertEquals(0, updated.stockCount)
        assertTrue(updated.soldOut)
    }

    @Test
    fun rejectsWhenAlreadySoldOut() {
        val result = SellPolicy.applySell(dish(stockCount = 5, soldOut = true), now = 1L)
        assertEquals(SellResult.Failure(SellError.SoldOut), result)
    }

    @Test
    fun rejectsWhenStockCountIsZero() {
        val result = SellPolicy.applySell(dish(stockCount = 0), now = 1L)
        assertEquals(SellResult.Failure(SellError.SoldOut), result)
    }

    @Test
    fun rejectsWhenStockDisabled() {
        val result = SellPolicy.applySell(dish(stockEnabled = false), now = 1L)
        assertEquals(SellResult.Failure(SellError.StockDisabled), result)
    }

    @Test
    fun rejectsWhenDisabled() {
        val result = SellPolicy.applySell(dish(enabled = false), now = 1L)
        assertEquals(SellResult.Failure(SellError.NotFound), result)
    }
}

class UnsellPolicyTest {

    private fun record(id: Long, deviceId: String = "A", revoked: Boolean = false) =
        SaleRecord(id = id, dishId = 1L, createdAt = 0L, deviceId = deviceId, revoked = revoked)

    @Test
    fun picksLatestActiveRecord() {
        val picked = UnsellPolicy.pickLastRevocable(listOf(record(1L), record(3L), record(2L)))
        assertEquals(3L, picked?.id)
    }

    @Test
    fun ignoresRevokedAndAllowsCrossDeviceRevoke() {
        val picked = UnsellPolicy.pickLastRevocable(
            listOf(record(1L), record(5L, deviceId = "B"), record(9L, revoked = true))
        )
        assertEquals(5L, picked?.id)
    }

    @Test
    fun returnsNullWhenNothingToRevoke() {
        assertNull(UnsellPolicy.pickLastRevocable(emptyList()))
    }

    @Test
    fun restoresStockAndClearsSoldOut() {
        val dish = Dish(
            id = 1L, categoryId = 1L, name = "烤串", priceCents = 300,
            stockEnabled = true, stockCount = 0, soldOut = true, updatedAt = 0L
        )
        val updated = UnsellPolicy.applyUnsell(dish, now = 50L)
        assertEquals(1, updated.stockCount)
        assertEquals(false, updated.soldOut)
        assertEquals(50L, updated.updatedAt)
    }
}
