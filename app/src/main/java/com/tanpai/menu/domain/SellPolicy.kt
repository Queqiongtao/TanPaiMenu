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

    /**
     * 取最后一条可撤销的卖出记录（全局，不限设备）。
     *
     * `docs/design/0020` 第 2.5 节：撤销不再按 deviceId 归属，手机与平板可互相撤销；
     * [records] 由 `SaleRecordDao.getActiveFor` 提供，已过滤掉已撤销记录。
     */
    fun pickLastRevocable(records: List<SaleRecord>): SaleRecord? =
        records.filter { !it.revoked }.maxByOrNull { it.id }

    fun applyUnsell(dish: Dish, now: Long): Dish =
        dish.copy(stockCount = dish.stockCount + 1, soldOut = false, updatedAt = now)
}
