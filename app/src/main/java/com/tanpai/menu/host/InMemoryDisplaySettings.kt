package com.tanpai.menu.host

import com.tanpai.menu.data.DisplaySettings
import com.tanpai.menu.data.DisplaySpec

/**
 * 主机侧测试用的展示设置替身（`docs/design/0023`）。
 *
 * 内存实现已移到 `data/InMemoryDisplaySettings`（生产代码不再需要它）；这里保留一个同名薄包装，
 * 让既有测试不必改 import。展示页走的是 [com.tanpai.menu.data.DisplaySettingsState]。
 */
class InMemoryDisplaySettings(
    override var backgroundStyleId: String? = null,
    override var backgroundImagePath: String? = null,
    override var backgroundScrimPercent: Int = DisplaySpec.DEFAULT_BACKGROUND_SCRIM_PERCENT,
    override var cardAlphaPercent: Int = DisplaySpec.DEFAULT_CARD_ALPHA_PERCENT,
    override var showStockHint: Boolean = true
) : DisplaySettings
