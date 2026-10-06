package com.tanpai.menu.domain

import kotlin.math.abs

/** 展示层的金额格式化：整数金额省略小数位（`¥10`），有角分时保留两位（`¥10.50`）。 */
object PriceFormatter {

    fun formatYuan(cents: Int): String {
        val sign = if (cents < 0) "-" else ""
        val value = abs(cents.toLong())
        val yuan = value / 100
        val fraction = value % 100
        return if (fraction == 0L) {
            "$sign¥$yuan"
        } else {
            "$sign¥$yuan.${fraction.toString().padStart(2, '0')}"
        }
    }
}
