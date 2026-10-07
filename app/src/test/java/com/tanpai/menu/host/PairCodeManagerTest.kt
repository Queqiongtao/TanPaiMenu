package com.tanpai.menu.host

import com.tanpai.menu.data.PairCodeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一次性 6 位配对码规则单测（`docs/design/0019` 第 2.1、5 节）：
 * 生成、过期、一次性、连续失败锁定、锁定内正确码拒绝、重新生成清零。
 */
class PairCodeManagerTest {

    private var now = 1_000_000L
    private val store = FakeStore()

    private fun manager(random: Int = 123456) =
        PairCodeManager(store = store, now = { now }, nextRandom = { random })

    @Test
    fun generateProducesPaddedSixDigitCode() {
        assertEquals("123456", manager(123456).generate())
        assertEquals("000042", manager(42).generate())
    }

    @Test
    fun verifyAcceptsAndConsumesCode() {
        val manager = manager()
        manager.generate()

        assertEquals(PairVerdict.Ok, manager.verify("123456", "手机A"))

        assertNull("配对成功后应作废配对码", store.pairCode)
        assertEquals("手机A", store.pairedDeviceName)
        assertEquals(PairVerdict.Invalid, manager.verify("123456", "手机B"))
    }

    @Test
    fun codeExpiresAfterFiveMinutes() {
        val manager = manager()
        manager.generate()

        now += PairCodeManager.TTL_MS - 1
        assertEquals(PairVerdict.Ok, manager.verify("123456", "手机A"))

        val expired = manager()
        expired.generate()
        now += PairCodeManager.TTL_MS
        assertEquals(PairVerdict.Invalid, expired.verify("123456", "手机A"))
    }

    @Test
    fun fiveWrongAttemptsLockForSixtySeconds() {
        val manager = manager()
        manager.generate()

        repeat(PairCodeManager.MAX_FAILURES) {
            assertEquals(PairVerdict.Invalid, manager.verify("000000", "手机A"))
        }
        val verdict = manager.verify("123456", "手机A")
        assertTrue("锁定后应返回剩余秒数", verdict is PairVerdict.Locked)
        assertEquals(60, (verdict as PairVerdict.Locked).remainingSeconds)
    }

    @Test
    fun correctCodeIsRejectedWhileLocked() {
        val manager = manager()
        manager.generate()
        repeat(PairCodeManager.MAX_FAILURES) { manager.verify("000000", "手机A") }

        val verdict = manager.verify("123456", "手机A")
        assertTrue("锁定期间即使码正确也应拒绝", verdict is PairVerdict.Locked)
        assertEquals("锁定期间不应消费配对码", "123456", store.pairCode)

        now += PairCodeManager.LOCK_MS + 1
        assertEquals("锁定到期后正确码应可用", PairVerdict.Ok, manager.verify("123456", "手机A"))
    }

    @Test
    fun wrongCodeIncrementsCounter() {
        val manager = manager()
        manager.generate()

        assertEquals(PairVerdict.Invalid, manager.verify("000000", "手机A"))
        assertEquals(1, store.pairCodeFailures)
        assertEquals(0L, store.pairCodeLockUntil)
    }

    @Test
    fun generateResetsFailuresAndLock() {
        val manager = manager()
        manager.generate()
        repeat(PairCodeManager.MAX_FAILURES) { manager.verify("000000", "手机A") }
        assertEquals(0, store.pairCodeFailures)
        assertTrue(store.pairCodeLockUntil > now)

        manager.generate()

        assertEquals(0, store.pairCodeFailures)
        assertEquals(0L, store.pairCodeLockUntil)
        assertNull("重新生成后清空「已配对」提示", store.pairedDeviceName)
        assertEquals(PairVerdict.Ok, manager.verify("123456", "手机A"))
    }

    @Test
    fun statusHidesExpiredCodeAndReportsSeconds() {
        val manager = manager()
        manager.generate()

        val fresh = manager.status()
        assertEquals("123456", fresh.code)
        assertEquals(300, fresh.secondsLeft)
        assertEquals(0, fresh.lockedSeconds)

        now += PairCodeManager.TTL_MS
        val expired = manager.status()
        assertNull(expired.code)
        assertEquals(0, expired.secondsLeft)
    }

    @Test
    fun statusReportsLockAndPairedDevice() {
        val manager = manager()
        manager.generate()
        repeat(PairCodeManager.MAX_FAILURES) { manager.verify("000000", "手机A") }

        val locked = manager.status()
        assertEquals(60, locked.lockedSeconds)

        now += PairCodeManager.LOCK_MS + 1
        manager.generate()
        manager.verify("123456", "手机B")
        assertEquals("手机B", manager.status().pairedDeviceName)
    }

    @Test
    fun isValidFormatRequiresExactlySixDigits() {
        assertTrue(PairCodeManager.isValidFormat("123456"))
        assertTrue(PairCodeManager.isValidFormat("000000"))
        assertFalse(PairCodeManager.isValidFormat("12345"))
        assertFalse(PairCodeManager.isValidFormat("1234567"))
        assertFalse(PairCodeManager.isValidFormat("12345a"))
        assertFalse(PairCodeManager.isValidFormat(""))
    }

    private class FakeStore : PairCodeStore {
        override var pairCode: String? = null
        override var pairCodeExpiresAt: Long = 0L
        override var pairCodeFailures: Int = 0
        override var pairCodeLockUntil: Long = 0L
        override var pairedDeviceName: String? = null
    }
}