package com.tanpai.menu.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 展示页店名招牌配色。
 *
 * 只服务于「店名」这一处视觉焦点：紫罗兰横向渐变底 + 半透明白描边 + 深紫投影，
 * 与 Material3 主题色解耦，避免全局配色调整影响招牌观感；菜名、徽标等仍走主题色。
 *
 * 对比度：白色文字在渐变两端约为 6.6:1 与 4.4:1，均满足大字 AA（≥3:1）。
 * 注：`androidx.compose.ui.draw.shadow` 的自定义阴影色需 API 28+，API 24 上使用系统默认阴影色。
 */
object StoreNameColors {
    /** 渐变起点（左）。 */
    val GradientStart = Color(0xFF5B3FD6)

    /** 渐变终点（右）。 */
    val GradientEnd = Color(0xFF9257E8)

    /** 1dp 描边：白色 35%。 */
    val Border = Color(0x59FFFFFF)

    /** 8dp 投影：深紫 45%。 */
    val Shadow = Color(0x733A1E85)
}