package com.tanpai.menu.domain

import kotlin.math.abs

object PriceFormatter {

    fun formatYuan(cents: Int): String {
        val sign = if (cents < 0) "-" else ""
        val value = abs(cents.toLong())
        val fraction = (value % 100).toString().padStart(2, '0')
        return "$sign¥${value / 100}.$fraction"
    }
}
