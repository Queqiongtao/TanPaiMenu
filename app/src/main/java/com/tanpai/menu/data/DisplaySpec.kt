package com.tanpai.menu.data

/**
 * 展示设置的取值范围（`docs/design/0023` 第 2.2 节）。
 *
 * 单一真源：`SettingsStore`（落盘时的钳制）、`HostApi`（`PATCH /display` 校验）
 * 与手机端滑条范围都引用这里，避免三处各写一套数字而漂移。
 */
object DisplaySpec {
    /** 卡片底不透明度默认值（百分比）。 */
    const val DEFAULT_CARD_ALPHA_PERCENT = 15

    /** 卡片底不透明度下限（百分比），避免卡片完全隐形。 */
    const val MIN_CARD_ALPHA_PERCENT = 15

    /** 背景蒙版默认不透明度（百分比）。 */
    const val DEFAULT_BACKGROUND_SCRIM_PERCENT = 25

    /** 背景蒙版不透明度下限（百分比）。 */
    const val MIN_BACKGROUND_SCRIM_PERCENT = 0

    /** 背景蒙版不透明度上限（百分比），过高会完全遮住背景图。 */
    const val MAX_BACKGROUND_SCRIM_PERCENT = 80
}
