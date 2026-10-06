package com.tanpai.menu.host

/**
 * 在线设备（`docs/design/0016` 第 2.4 节）。
 *
 * 只描述「当前正连着 WS 的手机」，不含离线历史；数据由 `HostServer.onlineDevices()` 从
 * 实时连接快照映射而来，UI 层不接触传输实现。
 */
data class OnlineDevice(
    val deviceId: String,
    val name: String,
    val role: String,
    val connectedAtMillis: Long
)