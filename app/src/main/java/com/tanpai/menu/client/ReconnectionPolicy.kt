package com.tanpai.menu.client

/**
 * 断线重连退避（`docs/design/0019` 第 2.2 节）：1 / 2 / 4 / 8 / 15 秒，之后固定在 15 秒无限重试。
 *
 * 退避只在「连接失败或连接断开」后推进；连接成功即归零。
 */
object ReconnectionPolicy {

    private val DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000)

    fun delayMillis(attempt: Int): Long = DELAYS_MS[attempt.coerceIn(0, DELAYS_MS.lastIndex)]
}