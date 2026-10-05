package com.tanpai.menu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedMenuTest {

    @Test
    fun seedHasSingleWontonCategory() {
        assertEquals(1, SeedMenu.categories.size)
        val category = SeedMenu.categories.single().category
        assertEquals("馄饨", category.name)
        assertEquals(1, category.sortOrder)
    }

    @Test
    fun seedHasTwelveDishesWithExpectedNamesAndPrices() {
        val expected = listOf(
            "原味鲜肉" to 1000,
            "韭菜馄饨" to 1000,
            "玉米馄饨" to 1200,
            "臭香皮蛋" to 1200,
            "贡菜馄饨" to 1200,
            "香菇马蹄" to 1200,
            "虾仁鲜肉" to 1200,
            "全家福" to 2000,
            "荠菜鲜肉" to 1200,
            "辣白菜鲜肉" to 1200,
            "咸蛋黄鲜肉" to 1400,
            "芝士玉米鲜肉" to 1400
        )
        val dishes = SeedMenu.categories.single().dishes
        assertEquals(expected.size, dishes.size)
        assertEquals(expected.map { it.first }, dishes.map { it.name })
        assertEquals(expected.map { it.second }, dishes.map { it.priceCents })
    }

    @Test
    fun seedDishesDisableStockAndUsePortionUnit() {
        val dishes = SeedMenu.categories.single().dishes
        assertTrue(dishes.all { !it.stockEnabled })
        assertTrue(dishes.all { it.unit == "份" })
        assertFalse(dishes.any { it.soldOut })
        assertEquals((1..dishes.size).toList(), dishes.map { it.sortOrder })
    }
}