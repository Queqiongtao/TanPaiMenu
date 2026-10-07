package com.tanpai.menu.data

/**
 * 一次性配对码的存储抽象（`docs/design/0019` 第 2.1 节）。
 *
 * 由 [SettingsStore] 实现；`host/PairCodeManager` 只依赖本接口，便于 JVM 单测注入内存实现。
 */
interface PairCodeStore {
    /** 当前配对码；未生成或已作废为 null。 */
    var pairCode: String?

    /** 配对码过期时间（UTC 毫秒）；0 表示无有效配对码。 */
    var pairCodeExpiresAt: Long

    /** 连续失败次数。 */
    var pairCodeFailures: Int

    /** 锁定截止时间（UTC 毫秒）；锁定期间即使码正确也拒绝。 */
    var pairCodeLockUntil: Long

    /** 最近一次配对成功的设备名（设置页提示用）。 */
    var pairedDeviceName: String?
}