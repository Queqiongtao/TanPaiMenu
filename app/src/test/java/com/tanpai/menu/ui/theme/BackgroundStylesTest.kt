package com.tanpai.menu.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置背景样式注册表的静态约束：样式清单是展示页与管理页共用的唯一真源，
 * 这里锁定数量、唯一性与查表行为，避免后续增删样式时出现重复 id 或漏配字符串资源。
 */
class BackgroundStylesTest {

    @Test
    fun hasSixBuiltInStyles() {
        assertEquals(6, BackgroundStyles.All.size)
    }

    @Test
    fun idsAreUniqueAndNotBlank() {
        val ids = BackgroundStyles.All.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotBlank() })
    }

    @Test
    fun labelsAreUnique() {
        val labels = BackgroundStyles.All.map { it.labelRes }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun everyStyleHasAtLeastTwoGradientStops() {
        assertTrue(BackgroundStyles.All.all { it.gradient.size >= 2 })
    }

    @Test
    fun byIdReturnsNullForMissingId() {
        assertNull(BackgroundStyles.byId(null))
        assertNull(BackgroundStyles.byId(""))
        assertNull(BackgroundStyles.byId("   "))
        assertNull(BackgroundStyles.byId("not_a_style"))
    }

    @Test
    fun byIdFindsEveryStyle() {
        BackgroundStyles.All.forEach { style ->
            assertSame(style, BackgroundStyles.byId(style.id))
        }
    }
}