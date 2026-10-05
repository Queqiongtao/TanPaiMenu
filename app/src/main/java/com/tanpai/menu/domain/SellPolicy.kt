package com.tanpai.menu.domain

import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.data.db.SaleRecord

sealed interface SellError {
    data object NotFound : SellError
    data object SoldOut : SellError
    data object StockDisabled : SellError
    data object NoRevocableRecord : SellError
}

sealed interface SellResult {
    data class Success(val dish: Dish) : SellResult
    data class Failure(val error: SellError) : SellResult
}

object SellPolicy {

    fun applySell(dish: Dish, now: Long): SellResult {
        if (!dish.enabled) return SellResult.Failure(SellError.NotFound)
        if (!dish.stockEnabled) return SellResult.Failure(SellError.StockDisabled)
        if (dish.soldOut || dish.stockCount <= 0) return SellResult.Failure(SellError.SoldOut)
        val remaining = dish.stockCount - 1
        return SellResult.Success(
            dish.copy(stockCount = remaining, soldOut = remaining == 0, updatedAt = now)
        )
    }
}

object UnsellPolicy {

    fun pickLastRevocable(records: List<SaleRecord>, deviceId: String): SaleRecord? =
        records.filter { it.deviceId == deviceId && !it.revoked }.maxByOrNull { it.id }

    fun applyUnsell(dish: Dish, now: Long): Dish =
        dish.copy(stockCount = dish.stockCount + 1, soldOut = false, updatedAt = now)
}
