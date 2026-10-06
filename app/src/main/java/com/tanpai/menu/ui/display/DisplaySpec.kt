package com.tanpai.menu.ui.display

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 卡片内部形态：宽卡用单行三列，窄卡改成上下两行。 */
enum class DishCardLayout { SingleRow, TwoRow }

/** 顶栏尺寸规格，按顶栏可用宽分档。 */
data class TopBarSpec(
    val height: Dp,
    val storeNameCapsuleHeight: Dp,
    val storeNameMaxWidth: Dp,
    val storeNameSize: TextUnit,
    val storeNameLineHeight: TextUnit,
    val storeNameSmallSize: TextUnit,
    val storeNameSmallLineHeight: TextUnit,
    val storeNameHorizontalPadding: Dp,
    val wechatQrBadgeSize: Dp,
    val wechatQrHintSize: TextUnit,
    val categoryChipSize: TextUnit
)

/**
 * 展示页尺寸规格：由运行时可用宽高推导，不按设备写死。
 *
 * [priceColumnWidth] / [statusColumnWidth] 只服务单行档的定宽三列；[DishCardLayout.TwoRow] 下为 `null`，
 * 两行卡片的价格胶囊与状态徽标宽度自适应。规则与档位表见 `docs/design/0013-横竖屏与多尺寸自适应.md`。
 */
data class DisplaySpec(
    val columns: Int,
    val cardLayout: DishCardLayout,
    val cardHeight: Dp,
    val cardHorizontalPadding: Dp,
    val cardVerticalPadding: Dp,
    val thumbnailSize: Dp,
    val thumbnailGap: Dp,
    val columnGap: Dp,
    val priceColumnWidth: Dp?,
    val priceTagMinWidth: Dp,
    val priceTextSize: TextUnit,
    val priceUnitSize: TextUnit,
    val statusColumnWidth: Dp?,
    val statusTextSize: TextUnit,
    val gridColumnSpacing: Dp,
    val gridRowSpacing: Dp
) {
    companion object {
        /** 单行档缺省规格（2 列），供预览与测试使用。 */
        val Default: DisplaySpec = singleRowSpec(columns = 2)

        /** 两行档缺省规格（2 列），供预览与测试使用。 */
        val TwoRowDefault: DisplaySpec = twoRowSpec(columns = 2)
    }
}

/** 选档基准：一屏目标菜品数，固定不随分类实际菜品数变化。 */
private const val VisibleTarget = 12

/** 候选列数上限。 */
private const val MaxColumns = 4

/** 网格列距、行距（两档一致）。 */
private val GridColumnSpacing = 16.dp
private val GridRowSpacing = 12.dp

/** 单行档的单卡最小宽度：再窄就放不下「缩略图 + 菜名 + 价格 + 徽标」。 */
private val SingleRowMinCardWidth = 380.dp
/** 两行档的单卡最小宽度。 */
private val TwoRowMinCardWidth = 240.dp
/** 顶栏宽档阈值：窄于该值用紧凑顶栏。 */
private val NarrowTopBarWidth = 720.dp

private val SingleRowCardHeight = 68.dp
private val SingleRowCardHorizontalPadding = 10.dp
private val SingleRowCardVerticalPadding = 6.dp
private val SingleRowThumbnailSize = 56.dp
private val SingleRowThumbnailGap = 8.dp
private val SingleRowColumnGap = 5.dp
private val SingleRowPriceColumnWidth = 128.dp
private val SingleRowPriceTagMinWidth = 96.dp
private val SingleRowPriceTextSize = 22.sp
private val SingleRowPriceUnitSize = 13.sp
private val SingleRowStatusColumnWidth = 68.dp
private val SingleRowStatusTextSize = 14.sp

private val TwoRowCardHeight = 96.dp
private val TwoRowCardHorizontalPadding = 8.dp
private val TwoRowCardVerticalPadding = 8.dp
private val TwoRowThumbnailSize = 44.dp
private val TwoRowThumbnailGap = 8.dp
private val TwoRowColumnGap = 8.dp
private val TwoRowPriceTagMinWidth = 72.dp
private val TwoRowPriceTextSize = 20.sp
private val TwoRowPriceUnitSize = 12.sp
private val TwoRowStatusTextSize = 12.sp

private val WideTopBarSpec = TopBarSpec(
    height = 72.dp,
    storeNameCapsuleHeight = 68.dp,
    storeNameMaxWidth = 320.dp,
    storeNameSize = 48.sp,
    storeNameLineHeight = 56.sp,
    storeNameSmallSize = 36.sp,
    storeNameSmallLineHeight = 44.sp,
    storeNameHorizontalPadding = 20.dp,
    wechatQrBadgeSize = 56.dp,
    wechatQrHintSize = 13.sp,
    categoryChipSize = 16.sp
)

private val NarrowTopBarSpec = TopBarSpec(
    height = 60.dp,
    storeNameCapsuleHeight = 52.dp,
    storeNameMaxWidth = 240.dp,
    storeNameSize = 32.sp,
    storeNameLineHeight = 40.sp,
    storeNameSmallSize = 26.sp,
    storeNameSmallLineHeight = 32.sp,
    storeNameHorizontalPadding = 16.dp,
    wechatQrBadgeSize = 48.dp,
    wechatQrHintSize = 12.sp,
    categoryChipSize = 14.sp
)

/**
 * 按网格可用宽高给出展示页规格。
 *
 * 1. 单行档：取最大列数使单卡宽 ≥ [SingleRowMinCardWidth]，若能一屏放下 [VisibleTarget] 道即采用；
 * 2. 否则两行档：取最大列数使单卡宽 ≥ [TwoRowMinCardWidth]，同样达标即采用；
 * 3. 两档都放不下（如手机竖屏）→ 取可见菜品数更多的档，平手取单行档（字大优先），允许滚动。
 */
fun displaySpecFor(width: Dp, height: Dp): DisplaySpec {
    val singleColumns = columnsFor(width, SingleRowMinCardWidth)
    val twoColumns = columnsFor(width, TwoRowMinCardWidth)
    val singleVisible = singleColumns * visibleRows(height, SingleRowCardHeight)
    val twoVisible = twoColumns * visibleRows(height, TwoRowCardHeight)
    val useSingleRow = when {
        singleVisible >= VisibleTarget -> true
        twoVisible >= VisibleTarget -> false
        else -> singleVisible >= twoVisible
    }
    return when {
        useSingleRow && singleColumns >= 1 -> singleRowSpec(singleColumns)
        twoColumns >= 1 -> twoRowSpec(twoColumns)
        else -> twoRowSpec(columns = 1)
    }
}

/** 顶栏规格：宽于阈值用大档，否则用小档。 */
fun topBarSpecFor(width: Dp): TopBarSpec =
    if (width >= NarrowTopBarWidth) WideTopBarSpec else NarrowTopBarSpec

/** 能放下 [minCardWidth] 宽单卡的最大列数（含列距），0 表示单卡都放不下。 */
private fun columnsFor(width: Dp, minCardWidth: Dp): Int {
    val fit = ((width.value + GridColumnSpacing.value) / (minCardWidth.value + GridColumnSpacing.value)).toInt()
    return fit.coerceIn(0, MaxColumns)
}

/** 给定高度能完整显示的行数，至少 1 行。 */
private fun visibleRows(height: Dp, cardHeight: Dp): Int {
    val rows = ((height.value + GridRowSpacing.value) / (cardHeight.value + GridRowSpacing.value)).toInt()
    return rows.coerceAtLeast(1)
}

private fun singleRowSpec(columns: Int) = DisplaySpec(
    columns = columns,
    cardLayout = DishCardLayout.SingleRow,
    cardHeight = SingleRowCardHeight,
    cardHorizontalPadding = SingleRowCardHorizontalPadding,
    cardVerticalPadding = SingleRowCardVerticalPadding,
    thumbnailSize = SingleRowThumbnailSize,
    thumbnailGap = SingleRowThumbnailGap,
    columnGap = SingleRowColumnGap,
    priceColumnWidth = SingleRowPriceColumnWidth,
    priceTagMinWidth = SingleRowPriceTagMinWidth,
    priceTextSize = SingleRowPriceTextSize,
    priceUnitSize = SingleRowPriceUnitSize,
    statusColumnWidth = SingleRowStatusColumnWidth,
    statusTextSize = SingleRowStatusTextSize,
    gridColumnSpacing = GridColumnSpacing,
    gridRowSpacing = GridRowSpacing
)

private fun twoRowSpec(columns: Int) = DisplaySpec(
    columns = columns,
    cardLayout = DishCardLayout.TwoRow,
    cardHeight = TwoRowCardHeight,
    cardHorizontalPadding = TwoRowCardHorizontalPadding,
    cardVerticalPadding = TwoRowCardVerticalPadding,
    thumbnailSize = TwoRowThumbnailSize,
    thumbnailGap = TwoRowThumbnailGap,
    columnGap = TwoRowColumnGap,
    priceColumnWidth = null,
    priceTagMinWidth = TwoRowPriceTagMinWidth,
    priceTextSize = TwoRowPriceTextSize,
    priceUnitSize = TwoRowPriceUnitSize,
    statusColumnWidth = null,
    statusTextSize = TwoRowStatusTextSize,
    gridColumnSpacing = GridColumnSpacing,
    gridRowSpacing = GridRowSpacing
)