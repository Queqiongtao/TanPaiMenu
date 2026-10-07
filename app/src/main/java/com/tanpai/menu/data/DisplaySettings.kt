package com.tanpai.menu.data

/**
 * 展示设置的读写抽象（`docs/design/0023` 第 2.2 节）。
 *
 * 生产环境由 [SettingsStore] 落地到 `SharedPreferences`；测试用 [InMemoryDisplaySettings]。
 * 「改哪些字段、互斥、清除」的补丁语义在 [DisplaySettingsState.applyDisplay] 里实现，
 * 本接口只负责存取值。
 */
interface DisplaySettings {
    /** 内置背景样式 id；null 表示未选内置样式。 */
    var backgroundStyleId: String?

    /** 自定义背景图相对路径 `images/<uuid>.jpg`；null 表示未设置。 */
    var backgroundImagePath: String?

    /** 白色蒙版不透明度百分比。 */
    var backgroundScrimPercent: Int

    /** 菜品卡片底色不透明度百分比。 */
    var cardAlphaPercent: Int

    /** 是否显示「有货 / 已售完 / 余 N」提示。 */
    var showStockHint: Boolean
}

/** 展示设置的内存实现：供测试与未接入真实设置时使用；默认值与 `SettingsStore` 一致。 */
class InMemoryDisplaySettings(
    override var backgroundStyleId: String? = null,
    override var backgroundImagePath: String? = null,
    override var backgroundScrimPercent: Int = DisplaySpec.DEFAULT_BACKGROUND_SCRIM_PERCENT,
    override var cardAlphaPercent: Int = DisplaySpec.DEFAULT_CARD_ALPHA_PERCENT,
    override var showStockHint: Boolean = true
) : DisplaySettings
