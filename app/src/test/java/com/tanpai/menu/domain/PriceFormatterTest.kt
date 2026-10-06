package com.tanpai.menu.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceFormatterTest {

    @Test
    fun formatsZero() {
        assertEquals("¥0", PriceFormatter.formatYuan(0))
    }

    @Test
    fun formatsWholeYuanWithoutDecimals() {
        assertEquals("¥12", PriceFormatter.formatYuan(1200))
    }

    @Test
    fun formatsJiaoAndFenWithTwoDecimals() {
        assertEquals("¥10.50", PriceFormatter.formatYuan(1050))
        assertEquals("¥3.05", PriceFormatter.formatYuan(305))
    }

    @Test
    fun formatsMaxValue() {
        assertEquals("¥9999.99", PriceFormatter.formatYuan(999999))
    }

    @Test
    fun formatsNegative() {
        assertEquals("-¥1", PriceFormatter.formatYuan(-100))
    }
}
