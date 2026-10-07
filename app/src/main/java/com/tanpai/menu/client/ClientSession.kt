package com.tanpai.menu.client

import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.net.HostAddress
import com.tanpai.menu.net.HostApiException
import com.tanpai.menu.net.HostRestClient
import com.tanpai.menu.net.MenuSocketClient
import com.tanpai.menu.net.SocketMessage
import com.tanpai.menu.net.protocol.CategoryDto
import com.tanpai.menu.net.protocol.DishDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 客户端连接状态（`docs/design/0019` 第 2.2 节）。 */
enum class ClientConnectionState {
    /** 未配对或已停止。 */
    DISCONNECTED,

    /** 正在拉快照 / 建立订阅。 */
    CONNECTING,

    /** 已连上并保持同步。 */
    ONLINE,

    /** 断线，正在退避重试（此时界面按只读处理）。 */
    RECONNECTING,

    /** 配对失效（401 或 WS 4401），需要重新配对。 */
    UNAUTHORIZED
}

/** 客户端界面状态：连接信息 + 菜单镜像快照。 */
data class ClientMenuState(
    val connection: ClientConnectionState = ClientConnectionState.DISCONNECTED,
    val hostAddress: String? = null,
    val error: String? = null,
    val version: Long = 0L,
    val categories: List<CategoryDto> = emptyList(),
    val dishes: List<DishDto> = emptyList()
) {
    /** 是否处于「只读」：未连上时界面禁用一切写入入口（M10 本身只读）。 */
    val readOnly: Boolean get() = connection != ClientConnectionState.ONLINE
}

/**
 * 客户端连接会话（`docs/design/0019` 第 2.2 节）。
 *
 * 单协程串行执行「拉快照 → 订阅 WS → 应用事件 → 断线退避重连」，对对端只暴露 [state] 快照。
 * 配对凭据来自 [SettingsStore]；配对失效（401 / 4401）时停止重试并要求用户重新配对。
 */
class ClientSession(
    private val settings: SettingsStore,
    private val rest: HostRestClient,
    private val sockets: MenuSocketClient
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mirror = ClientMirror()
    private val _state = MutableStateFlow(ClientMenuState())
    val state: StateFlow<ClientMenuState> = _state.asStateFlow()

    private var job: Job? = null
    private var connection = ClientConnectionState.DISCONNECTED
    private var lastError: String? = null

    /** 是否已持有主机地址与 token。 */
    val isPaired: Boolean
        get() = !settings.clientHostAddress.isNullOrBlank() && !settings.clientToken.isNullOrBlank()

    /** 启动连接循环；已在运行时不做重复启动（幂等）。 */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runLoop() }
    }

    /** 停止连接并清空镜像（退出客户端形态或重新配对时调用）。 */
    fun stop() {
        job?.cancel()
        job = null
        mirror.clear()
        connection = ClientConnectionState.DISCONNECTED
        lastError = null
        publish()
    }

    /** 重新开始连接：配对成功与「重连」按钮都走这里。 */
    fun reconnect() {
        stop()
        start()
    }

    /** 清除配对凭据并停止（手机端「重新配对」）。 */
    fun unpair() {
        settings.clearClientPairing()
        stop()
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val address = settings.clientHostAddress
            val token = settings.clientToken
            if (address.isNullOrBlank() || token.isNullOrBlank()) {
                connection = ClientConnectionState.DISCONNECTED
                lastError = null
                publish()
                return
            }
            try {
                connection = ClientConnectionState.CONNECTING
                lastError = null
                publish()

                mirror.applySnapshot(rest.state(address, token))
                connection = ClientConnectionState.ONLINE
                publish()
                attempt = 0

                val closeCode = subscribe(address, token)
                if (closeCode == CLOSE_UNAUTHORIZED) {
                    connection = ClientConnectionState.UNAUTHORIZED
                    lastError = "配对已失效，请重新配对"
                    publish()
                    return
                }
                connection = ClientConnectionState.RECONNECTING
                lastError = "与平板断开，正在重连"
                publish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HostApiException) {
                if (e.isUnauthorized) {
                    connection = ClientConnectionState.UNAUTHORIZED
                    lastError = "配对已失效，请重新配对"
                    publish()
                    return
                }
                connection = ClientConnectionState.RECONNECTING
                lastError = e.message
                publish()
            } catch (e: Exception) {
                connection = ClientConnectionState.RECONNECTING
                lastError = "无法连接平板，请确认手机与平板在同一 Wi-Fi"
                publish()
            }
            delay(ReconnectionPolicy.delayMillis(attempt))
            attempt++
        }
    }

    /** 订阅事件直到连接关闭，返回 WS 关闭码（未收到关闭帧为 null）。 */
    private suspend fun subscribe(address: String, token: String): Int? {
        val url = HostAddress.webSocketUrl(address, token, settings.deviceId, settings.clientDeviceName)
            ?: throw HostApiException(0, null, "主机地址无效")
        val session = sockets.open(url)
        try {
            for (message in session.messages) {
                when (message) {
                    is SocketMessage.Event -> {
                        if (mirror.applyEvent(message.event) == EventApplyResult.NEED_RESYNC) {
                            session.close()
                            break
                        }
                        publish()
                    }

                    SocketMessage.Unparsable -> {
                        session.close()
                        break
                    }
                }
            }
        } finally {
            session.close()
        }
        return session.awaitClose()
    }

    private fun publish() {
        _state.value = ClientMenuState(
            connection = connection,
            hostAddress = settings.clientHostAddress,
            error = lastError,
            version = mirror.version,
            categories = mirror.categories(),
            dishes = mirror.dishes()
        )
    }

    private companion object {
        /** 主机的 4401 关闭码：配对码 / token 失效。 */
        const val CLOSE_UNAUTHORIZED = 4401
    }
}