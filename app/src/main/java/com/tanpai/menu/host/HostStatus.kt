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
    private val server: () -> HostServer?
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

    /** 更换配对码并断开现有连接（旧码立即失效），返回新码。 */
    fun regeneratePairToken(): String {
        val token = settings.regeneratePairToken()
        server()?.kickAllConnections()
        return token
    }
}