package com.tanpai.menu.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 展示页价格胶囊配色。
 *
 * 卡片底不透明度可低至 15%，价格落在喜庆红背景上会与背景同色系，因此价格区固定加一层
 * 90% 白的胶囊底：红字落在白底上对比度约 6.2:1，满足正文文字 ≥ 4.5:1 的要求。
 *
 * 只服务展示页价格这一处，与 `StoreNameColors`、`StatusColors` 同构，不与容器主题色耦合：
 * 主题色或背景样式调整都不会动到价格观感。
 */
object PriceColors {
    /** 胶囊底：90% 白，在红底、图片背景与浅色背景上都成块可辨。 */
    val Container = Color(0xE6FFFFFF)

    /** 价格文字：喜庆红，与内置背景样式 `warm_red` 同色。 */
    val Content = Color(0xFFB32C1B)
}
