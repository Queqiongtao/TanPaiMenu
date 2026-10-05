package com.tanpai.menu.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 展示页状态徽标语义色。
 *
 * Material3 没有 success 角色，且基线亮色方案的 `tertiaryContainer` 是粉色，
 * 不适合表达「有货」，因此单独定义。仅用于展示页行尾状态列，不影响全局配色；
 * 有货 / 余量 / 售完三态的徽标形状与字号统一在 `ui/display/DisplayScreen.kt` 的
 * `StatusBadge` 中约束。
 *
 * 对比度：#1E7B34 on #E6F4EA 约 4.7:1，满足正文文字 ≥ 4.5:1 的要求。
 */
object StatusColors {
    /** 有货：浅绿底。 */
    val InStockContainer = Color(0xFFE6F4EA)

    /** 有货：深绿字。 */
    val InStockContent = Color(0xFF1E7B34)
}