package com.tanpai.menu.client

import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.net.HostAddress
import com.tanpai.menu.net.HostApiException
import com.tanpai.menu.net.HostRestClient
import com.tanpai.menu.net.MenuSocketClient
import com.tanpai.menu.net.SocketMessage
import com.tanpai.menu.net.protocol.CategoryDto
import com.tanpai.menu.net.protocol.DishDto
import com.tanpai.menu.net.protocol.StateDto
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    val dishes: List<DishDto> = emptyList(),
    val storeName: String? = null,
    val clientEditsEnabled: Boolean = true
) {
    /** 是否处于「只读」：未连上时界面禁用一切写入入口。 */
    val readOnly: Boolean get() = connection != ClientConnectionState.ONLINE

    /** 是否可编辑：在线且主机允许手机编辑（`docs/design/0020` 第 2.3 节）。 */
    val canEdit: Boolean get() = connection == ClientConnectionState.ONLINE && clientEditsEnabled
}

/** 提交失败（离线或主机未对齐）；[message] 为可直接展示的中文提示。 */
class ClientSubmitException(message: String) : IllegalStateException(message)

/**
 * 客户端连接会话（`docs/design/0019` 第 2.2 节、`docs/design/0020` 第 2.2 节）。
 *
 * 单协程串行执行「拉快照 → 订阅 WS → 应用事件 → 断线退避重连」，对对端只暴露 [state] 快照。
 * 写命令走 [submit]：提交期间由界面禁用该行，成功后等镜像版本前进（即等主机广播对齐），
 * 失败直接返回中文错误、不做回滚。配对凭据来自 [SettingsStore]；配对失效时停止重试。
 */
class ClientSession(
    private val settings: SettingsStore,
    private val rest: HostRestClient,
    private val sockets: MenuSocketClient
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mirror = ClientMirror()

    /** 镜像访问锁：连接循环、[submit] 的超时兜底与 [refreshState] 可能并发访问镜像。 */
    private val mirrorLock = Any()

    private val commands = ClientCommandExecutor(
        rest = rest,
        hostAddress = { settings.clientHostAddress.orEmpty() },
        token = { settings.clientToken.orEmpty() },
        deviceId = { settings.deviceId }
    )

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
        withMirror { mirror.clear() }
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

    /**
     * 提交一条写命令（`docs/design/0020` 第 2.2 节）。
     *
     * 离线直接失败；请求成功后等待镜像版本前进（主机广播对齐），最长 [SYNC_TIMEOUT_MILLIS]，
     * 超时则主动拉一次 `/state` 兜底；失败原样返回主机的中文错误，不做回滚。
     */
    suspend fun submit(command: ClientCommand): Result<Unit> {
        if (_state.value.connection != ClientConnectionState.ONLINE) {
            return Result.failure(ClientSubmitException(OFFLINE_MESSAGE))
        }
        val versionBeforeSubmit = _state.value.version
        val failure = runCatching { commands.execute(command) }.exceptionOrNull()
        if (failure != null) {
            // 编辑权限被关闭：拉一次快照刷新可编辑态，界面据此隐藏管理入口。
            if (failure is HostApiException && failure.status == HTTP_FORBIDDEN) {
                runCatching { refreshState() }
            }
            return Result.failure(failure)
        }
        awaitApplied(versionBeforeSubmit)
        return Result.success(Unit)
    }

    /**
     * 上传压缩后的图片字节，返回主机上的相对路径（`docs/design/0020` 第 2.4 节）。
     *
     * 图片上传不改变菜单版本，因此不与广播对齐，直接返回主机结果。
     */
    suspend fun uploadImage(bytes: ByteArray): Result<String> {
        val address = settings.clientHostAddress
        val token = settings.clientToken
        if (_state.value.connection != ClientConnectionState.ONLINE ||
            address.isNullOrBlank() ||
            token.isNullOrBlank()
        ) {
            return Result.failure(ClientSubmitException(OFFLINE_MESSAGE))
        }
        return runCatching { rest.uploadImage(address, token, bytes).imagePath }
    }

    /** 主动拉一次 `/state` 覆盖镜像（写入超时兜底与权限变化后刷新）。 */
    suspend fun refreshState() {
        val address = settings.clientHostAddress
        val token = settings.clientToken
        if (address.isNullOrBlank() || token.isNullOrBlank()) return
        val snapshot = rest.state(address, token)
        withMirror { mirror.applySnapshot(snapshot) }
        publish()
    }

    /** 等待主机广播对齐；写入已成功落库，这里只决定界面何时解除「提交中」。 */
    private suspend fun awaitApplied(versionBeforeSubmit: Long) {
        val applied = withTimeoutOrNull(SYNC_TIMEOUT_MILLIS) {
            state.first { it.version > versionBeforeSubmit }
        }
        if (applied == null) runCatching { refreshState() }
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

                val snapshot = rest.state(address, token)
                withMirror { mirror.applySnapshot(snapshot) }
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
                        val applied = withMirror { mirror.applyEvent(message.event) }
                        if (applied == EventApplyResult.NEED_RESYNC) {
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

    /** 串行访问镜像，保证状态发布与事件应用不会交叉。 */
    private fun <T> withMirror(block: () -> T): T = synchronized(mirrorLock) { block() }

    /** 发布状态：加锁读取镜像，保证发布的是完整一致的一份。 */
    private fun publish() {
        _state.value = withMirror {
            ClientMenuState(
                connection = connection,
                hostAddress = settings.clientHostAddress,
                error = lastError,
                version = mirror.version,
                categories = mirror.categories(),
                dishes = mirror.dishes(),
                storeName = mirror.storeName,
                clientEditsEnabled = mirror.clientEditsEnabled
            )
        }
    }

    private companion object {
        /** 主机的 4401 关闭码：配对码 / token 失效。 */
        const val CLOSE_UNAUTHORIZED = 4401

        /** 403：平板已关闭手机编辑。 */
        const val HTTP_FORBIDDEN = 403

        /** 等待主机广播对齐的上限；超时后主动拉快照兜底。 */
        const val SYNC_TIMEOUT_MILLIS = 5_000L

        /** 离线提交的统一提示（界面直接用「提交失败：<此文案>」展示）。 */
        const val OFFLINE_MESSAGE = "当前离线，无法提交修改"
    }
}