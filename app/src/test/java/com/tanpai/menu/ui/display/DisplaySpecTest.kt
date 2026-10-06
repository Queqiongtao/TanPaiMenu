package com.tanpai.menu.ui.display

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 展示页选档规则的穷举校验：列数、卡片形态与关键尺寸都由可用宽高推导，
 * 换设备只需落档、不改代码。场景取自 `docs/design/0013-横竖屏与多尺寸自适应.md` 的落档表。
 */
class DisplaySpecTest {

    @Test
    fun t10LandscapeKeepsSingleRowTwoColumns() {
        val spec = displaySpecFor(width = 937.5.dp, height = 497.dp)

        assertEquals(2L, spec.columns.toLong())
        assertEquals(DishCardLayout.SingleRow, spec.cardLayout)
        assertEquals(68.dp, spec.cardHeight)
        assertEquals(56.dp, spec.thumbnailSize)
        assertEquals(128.dp, spec.priceColumnWidth)
        assertEquals(96.dp, spec.priceTagMinWidth)
        assertEquals(22.sp, spec.priceTextSize)
        assertEquals(68.dp, spec.statusColumnWidth)
    }

    @Test
    fun t10PortraitSwitchesToTwoRowTwoColumns() {
        val spec = displaySpecFor(width = 577.dp, height = 821.dp)

        assertEquals(2L, spec.columns.toLong())
        assertEquals(DishCardLayout.TwoRow, spec.cardLayout)
        assertEquals(120.dp, spec.cardHeight)
        assertEquals(56.dp, spec.thumbnailSize)
        assertNull(spec.priceColumnWidth)
        assertNull(spec.statusColumnWidth)
        assertEquals(72.dp, spec.priceTagMinWidth)
        assertEquals(20.sp, spec.priceTextSize)
        assertEquals(12.sp, spec.statusTextSize)
    }

    @Test
    fun phonePortraitFallsBackToSingleColumnSingleRow() {
        val spec = displaySpecFor(width = 411.dp, height = 708.dp)

        assertEquals(1L, spec.columns.toLong())
        assertEquals(DishCardLayout.SingleRow, spec.cardLayout)
    }

    @Test
    fun thumbnailKeepsSameSizeInPortraitAndLandscape() {
        val landscape = displaySpecFor(width = 937.5.dp, height = 497.dp)
        val portrait = displaySpecFor(width = 577.dp, height = 821.dp)

        assertEquals(landscape.thumbnailSize, portrait.thumbnailSize)
        assertEquals(56.dp, portrait.thumbnailSize)
    }

    @Test
    fun wideScreenUsesThreeSingleRowColumns() {
        val spec = displaySpecFor(width = 1400.dp, height = 600.dp)

        assertEquals(3L, spec.columns.toLong())
        assertEquals(DishCardLayout.SingleRow, spec.cardLayout)
    }

    @Test
    fun extremelyNarrowScreenFallsBackToTwoRowSingleColumn() {
        val spec = displaySpecFor(width = 200.dp, height = 600.dp)

        assertEquals(1L, spec.columns.toLong())
        assertEquals(DishCardLayout.TwoRow, spec.cardLayout)
    }

    @Test
    fun topBarSwitchesToCompactOnNarrowWidth() {
        assertEquals(72.dp, topBarSpecFor(937.5.dp).height)
        assertEquals(48.sp, topBarSpecFor(937.5.dp).storeNameSize)

        assertEquals(60.dp, topBarSpecFor(577.dp).height)
        assertEquals(32.sp, topBarSpecFor(577.dp).storeNameSize)
        assertEquals(48.dp, topBarSpecFor(577.dp).wechatQrBadgeSize)
    }

    @Test
    fun defaultPresetsMatchWideLayout() {
        assertEquals(DishCardLayout.SingleRow, DisplaySpec.Default.cardLayout)
        assertEquals(DishCardLayout.TwoRow, DisplaySpec.TwoRowDefault.cardLayout)
        assertEquals(2L, DisplaySpec.Default.columns.toLong())
        assertEquals(2L, DisplaySpec.TwoRowDefault.columns.toLong())
    }
}