package com.tanpai.menu.host

import com.tanpai.menu.data.PairCodeStore
import java.security.SecureRandom

/** 配对码校验结果（`docs/design/0019` 第 2.1 节）。 */
sealed interface PairVerdict {
    /** 码正确且已消费（一次性）。 */
    data object Ok : PairVerdict

    /** 码错误或已过期。 */
    data object Invalid : PairVerdict

    /** 连续失败进入锁定，[remainingSeconds] 为剩余锁定秒数。 */
    data class Locked(val remainingSeconds: Int) : PairVerdict
}

/**
 * `HostApi` 依赖的最小校验接口。
 *
 * 由 [PairCodeManager] 实现；`HostApi` 因此不依赖偏好存储，JVM 单测可直接注入假实现。
 */
fun interface PairCodeVerifier {
    fun verify(code: String, deviceName: String): PairVerdict
}

/** 设置页展示用的配对码状态；[code] 为 null 表示当前没有可用配对码。 */
data class PairCodeStatus(
    val code: String?,
    val secondsLeft: Int,
    val lockedSeconds: Int,
    val pairedDeviceName: String?
)

/**
 * 一次性 6 位配对码（`docs/design/0019` 第 2.1 节）。
 *
 * 规则：6 位数字、有效期 5 分钟、配对成功立即作废、连续失败 5 次锁定 60 秒。
 * 时间与随机源都可注入，便于 JVM 单测覆盖过期与锁定边界。
 */
class PairCodeManager(
    private val store: PairCodeStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val nextRandom: () -> Int = { SecureRandom().nextInt(CODE_SPACE) }
) : PairCodeVerifier {

    /** 生成新码：覆盖旧码，清零失败计数与「已配对」提示。 */
    fun generate(): String {
        val code = "%06d".format(nextRandom().mod(CODE_SPACE))
        store.pairCode = code
        store.pairCodeExpiresAt = now() + TTL_MS
        store.pairCodeFailures = 0
        store.pairCodeLockUntil = 0L
        store.pairedDeviceName = null
        return code
    }

    /** 当前状态；过期视为没有配对码（不主动清库，等下次生成覆盖）。 */
    fun status(): PairCodeStatus {
        val left = store.pairCodeExpiresAt - now()
        val code = store.pairCode?.takeIf { it.length == CODE_LENGTH && left > 0 }
        val locked = store.pairCodeLockUntil - now()
        return PairCodeStatus(
            code = code,
            secondsLeft = if (code == null) 0 else (left / 1000).toInt(),
            lockedSeconds = if (locked > 0) (locked / 1000).toInt() else 0,
            pairedDeviceName = store.pairedDeviceName
        )
    }

    override fun verify(code: String, deviceName: String): PairVerdict {
        val locked = store.pairCodeLockUntil - now()
        if (locked > 0) {
            return PairVerdict.Locked((locked / 1000).toInt().coerceAtLeast(1))
        }
        val current = store.pairCode
        if (current == null || now() >= store.pairCodeExpiresAt) {
            return PairVerdict.Invalid
        }
        if (code != current) {
            val failures = store.pairCodeFailures + 1
            if (failures >= MAX_FAILURES) {
                store.pairCodeFailures = 0
                store.pairCodeLockUntil = now() + LOCK_MS
            } else {
                store.pairCodeFailures = failures
            }
            return PairVerdict.Invalid
        }
        store.pairCode = null
        store.pairCodeExpiresAt = 0L
        store.pairCodeFailures = 0
        store.pairCodeLockUntil = 0L
        store.pairedDeviceName = deviceName
        return PairVerdict.Ok
    }

    companion object {
        /** 配对码位数。 */
        const val CODE_LENGTH = 6

        /** 配对码有效期（5 分钟）。 */
        const val TTL_MS = 5L * 60 * 1000

        /** 连续失败上限，达到即锁定。 */
        const val MAX_FAILURES = 5

        /** 锁定时间（60 秒）。 */
        const val LOCK_MS = 60L * 1000

        /** 配对码取值空间（10^6）。 */
        private const val CODE_SPACE = 1_000_000

        /** 请求里的码必须是恰好 6 位数字。 */
        fun isValidFormat(code: String): Boolean =
            code.length == CODE_LENGTH && code.all { it in '0'..'9' }
    }
}