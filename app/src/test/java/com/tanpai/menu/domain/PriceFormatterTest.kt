package com.tanpai.menu.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceFormatterTest {

    @Test
    fun formatsZero() {
        assertEquals("¥0.00", PriceFormatter.formatYuan(0))
    }

    @Test
    fun formatsWholeYuan() {
        assertEquals("¥12.00", PriceFormatter.formatYuan(1200))
    }

    @Test
    fun formatsFraction() {
        assertEquals("¥3.05", PriceFormatter.formatYuan(305))
    }

    @Test
    fun formatsMaxValue() {
        assertEquals("¥9999.99", PriceFormatter.formatYuan(999999))
    }

    @Test
    fun formatsNegative() {
        assertEquals("-¥1.00", PriceFormatter.formatYuan(-100))
    }
}
