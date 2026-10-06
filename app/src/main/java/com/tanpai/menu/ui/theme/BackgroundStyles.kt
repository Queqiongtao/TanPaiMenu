package com.tanpai.menu.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.tanpai.menu.R

/**
 * 展示页内置背景样式（纯代码绘制，不打包图片资源）。
 *
 * 每款样式 = 一层线性渐变打底 + 一层居中径向光晕，均与绘制尺寸无关、任意分辨率清晰。
 * 本清单是唯一真源：展示页背景与管理页色卡预览共用，避免两处配色漂移。
 * 设计说明见 `docs/design/0009-展示页内置背景样式.md`。
 */
data class BackgroundStyle(
    /** 稳定标识，写入 `SettingsStore.backgroundStyleId`。 */
    val id: String,
    /** 样式中文名，取值为 `res/values/strings.xml` 中的字符串资源。 */
    val labelRes: Int,
    /** 线性渐变色标（左上 → 右下），至少两个。 */
    val gradient: List<Color>,
    /** 居中光晕色（不透明度已含在色值里），null 表示不加光晕。 */
    val glow: Color?
)

/**
 * 内置背景样式的注册表：6 款中式餐饮暖色渐变，按「再叠默认 30% 白蒙版后仍好看」调色。
 *
 * 前 5 款为暖色系，`bamboo` 作为唯一冷调点缀，避免色卡过于雷同。
 */
object BackgroundStyles {

    /** 全部内置样式，顺序即设置页色卡的展示顺序（3 列 × 2 行）。 */
    val All: List<BackgroundStyle> = listOf(
        BackgroundStyle(
            id = "warm_red",
            labelRes = R.string.background_style_warm_red,
            gradient = listOf(Color(0xFFB32C1B), Color(0xFF6E150C)),
            glow = Color(0x26FFD9A0)
        ),
        BackgroundStyle(
            id = "amber",
            labelRes = R.string.background_style_amber,
            gradient = listOf(Color(0xFFD97A21), Color(0xFF8A3B0C)),
            glow = Color(0x2EFFE7B8)
        ),
        BackgroundStyle(
            id = "caramel",
            labelRes = R.string.background_style_caramel,
            gradient = listOf(Color(0xFFB06E2C), Color(0xFF5A3010)),
            glow = Color(0x26E8C08A)
        ),
        BackgroundStyle(
            id = "rice_paper",
            labelRes = R.string.background_style_rice_paper,
            gradient = listOf(Color(0xFFEAD6B2), Color(0xFFC6A87A)),
            glow = Color(0x33FFFFFF)
        ),
        BackgroundStyle(
            id = "sauce_red",
            labelRes = R.string.background_style_sauce_red,
            gradient = listOf(Color(0xFF8A2620), Color(0xFF420E0B)),
            glow = Color(0x1FFFB27A)
        ),
        BackgroundStyle(
            id = "bamboo",
            labelRes = R.string.background_style_bamboo,
            gradient = listOf(Color(0xFF6E8A4E), Color(0xFF33471F)),
            glow = Color(0x26E6F0C8)
        )
    )

    /** 按 id 查样式；null、空白或未知 id 一律返回 null（展示页据此回退纯色底）。 */
    fun byId(id: String?): BackgroundStyle? {
        val wanted = id?.takeIf { it.isNotBlank() } ?: return null
        return All.firstOrNull { it.id == wanted }
    }

    /** 样式对应的绘制笔刷，顺序即叠绘顺序（先线性渐变打底，再叠居中光晕）。 */
    fun brushesOf(style: BackgroundStyle): List<Brush> = buildList {
        add(Brush.linearGradient(style.gradient, Offset.Zero, Offset.Infinite))
        style.glow?.let { add(Brush.radialGradient(listOf(it, Color.Transparent))) }
    }
}

/**
 * 按内置样式叠绘背景（渐变 + 光晕）。
 *
 * 必须在同一节点内链式叠绘：跨父子节点绘制时父节点会被子节点覆盖，描边会看不出来。
 */
fun Modifier.backgroundStyle(style: BackgroundStyle): Modifier =
    BackgroundStyles.brushesOf(style).fold(this) { modifier, brush ->
        modifier.background(brush)
    }