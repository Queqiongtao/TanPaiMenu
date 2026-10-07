package com.tanpai.menu.ui.display

/** 空闲自动滚动的轮询、等待与步进时长（`docs/design/0021` 第 3.3 节）。 */
internal const val AutoScrollPollMillis = 1_000L
internal const val AutoScrollIdleMillis = 15_000L
internal const val AutoScrollStepMillis = 6_000L

/**
 * 计算下一次自动滚动目标；没有溢出项时返回 null。
 *
 * 每次前进一行的距离；到末行后回到第一行，由调用方负责动画滚动。
 */
internal fun nextAutoScrollTarget(
    firstVisibleItemIndex: Int,
    itemCount: Int,
    columns: Int,
    visibleItemCount: Int
): Int? {
    if (columns <= 0 || itemCount <= 0 || itemCount <= visibleItemCount) return null
    val next = firstVisibleItemIndex + columns
    return if (next < itemCount) next else 0
}
