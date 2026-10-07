package com.tanpai.menu.host

import android.content.Context
import android.graphics.Bitmap
import com.tanpai.menu.data.SettingsStore

/**
 * 主机运行态的 UI 入口（`docs/design/0015` 第 2.7 节）。
 *
 * UI 只读这里的状态与配对信息，不直接接触 `HostServer`；服务实例由 `AppContainer`
 * 启动后通过 [server] 提供，未启动时为 null。
 */
class HostStatusProvider(
    private val context: Context,
    private val settings: SettingsStore,
    private val server: () -> HostServer?,
    private val pairCode: PairCodeManager
) {

    /** 服务是否在监听；端口全部被占用时为 false。 */
    val running: Boolean get() = server()?.isServing == true

    /** 实际绑定的端口；未启动为 0。 */
    val port: Int get() = server()?.port ?: 0

    val pairToken: String get() = settings.pairToken

    /** 当前局域网访问地址 `http://<ip>:<port>`；服务未启动或无 Wi-Fi 返回 null。 */
    fun address(): String? =
        if (running) HostInfo.address(HostInfo.lanIpv4(context), port) else null

    /** 二维码内容；无可用地址返回 null。 */
    fun qrContent(): String? =
        if (running) HostInfo.qrContent(HostInfo.lanIpv4(context), port, pairToken) else null

    /** 生成二维码位图；无可用地址或编码失败返回 null。 */
    fun qrBitmap(sizePx: Int): Bitmap? = qrContent()?.let { HostInfo.qrBitmap(it, sizePx) }

    /** 是否启用手机控制端（持久化在 SettingsStore.hostEnabled）。 */
    val hostEnabled: Boolean get() = settings.hostEnabled

    /**
     * 打开 / 关闭手机控制端：立即启停前台服务（docs/design/0016 第 2.2 节），返回新状态。
     *
     * 启停是异步的（服务在 IO 线程建 / 拆监听），调用方随后刷新一次即可，UI 另有 2 秒轮询兜底。
     */
    fun setHostEnabled(enabled: Boolean): Boolean {
        settings.hostEnabled = enabled
        if (enabled) {
            HostService.start(context)
        } else {
            HostService.stop(context)
        }
        return enabled
    }

    /** 当前在线设备快照（docs/design/0016 第 2.4 节）；服务未启动时为空。 */
    fun onlineDevices(): List<OnlineDevice> = server()?.onlineDevices().orEmpty()

    /** 设置页「显示配对码」：生成一次性 6 位配对码（覆盖旧码并清零失败计数）。 */
    fun generatePairCode(): String = pairCode.generate()

    /** 当前一次性配对码状态：码、剩余秒数、锁定剩余秒数与最近配对的设备名。 */
    fun pairCodeStatus(): PairCodeStatus = pairCode.status()

    /** 更换长期令牌并断开现有连接（旧令牌立即失效），返回新令牌。 */
    fun regeneratePairToken(): String {
        val token = settings.regeneratePairToken()
        server()?.kickAllConnections()
        return token
    }
}