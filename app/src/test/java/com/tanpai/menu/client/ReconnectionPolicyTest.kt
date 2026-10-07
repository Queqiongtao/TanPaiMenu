package com.tanpai.menu.client

import org.junit.Assert.assertEquals
import org.junit.Test

/** 重连退避序列单测（`docs/design/0019` 第 2.2 节）：1/2/4/8/15 秒后固定 15 秒。 */
class ReconnectionPolicyTest {

    @Test
    fun backoffGrowsThenStaysAtFifteenSeconds() {
        assertEquals(1_000L, ReconnectionPolicy.delayMillis(0))
        assertEquals(2_000L, ReconnectionPolicy.delayMillis(1))
        assertEquals(4_000L, ReconnectionPolicy.delayMillis(2))
        assertEquals(8_000L, ReconnectionPolicy.delayMillis(3))
        assertEquals(15_000L, ReconnectionPolicy.delayMillis(4))
        assertEquals(15_000L, ReconnectionPolicy.delayMillis(5))
        assertEquals(15_000L, ReconnectionPolicy.delayMillis(100))
    }

    @Test
    fun negativeAttemptFallsBackToFirstDelay() {
        assertEquals(1_000L, ReconnectionPolicy.delayMillis(-1))
    }
}