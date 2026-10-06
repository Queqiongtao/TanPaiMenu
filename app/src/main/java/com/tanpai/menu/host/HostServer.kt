package com.tanpai.menu.host

import com.tanpai.menu.data.MenuChange
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.net.protocol.DeviceDto
import com.tanpai.menu.net.protocol.DeviceRole
import com.tanpai.menu.net.protocol.ErrorCode
import com.tanpai.menu.net.protocol.EventType
import com.tanpai.menu.net.protocol.Iso8601
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 主机 HTTP + WebSocket 服务（`docs/design/0015` 第 2 章）。
 *
 * NanoHTTPD 只做传输搬运：HTTP ↔ `HostRequest`/`HostResponse`、multipart 取文件、`/ws` 握手；
 * 业务全部交给 [HostApi] 与 [RepositoryMenuBackend]，容器层不含业务判断。
 */
class HostServer private constructor(
    private val api: HostApi,
    private val backend: RepositoryMenuBackend,
    private val repository: MenuRepository,
    private val tokenProvider: () -> String,
    private val scope: CoroutineScope,
    private val log: (String, Throwable?) -> Unit,
    private val bindPort: Int
) : NanoWSD("0.0.0.0", bindPort) {

    private val hub = WsHub()

    @Volatile
    private var started = false

    private var changesJob: Job? = null

    /** 实际绑定的端口；未启动成功时为 0。 */
    val port: Int get() = if (started) bindPort else 0

    val isServing: Boolean get() = started

    /** 启动监听并订阅写事件广播；端口被占用等绑定失败返回 false。 */
    fun startServing(): Boolean {
        if (started) return true
        return try {
            start(READ_TIMEOUT_MS, true)
            started = true
            changesJob = scope.launch {
                repository.events.collect { change -> broadcastChange(change) }
            }
            log("主机服务已启动，端口 $bindPort", null)
            true
        } catch (e: IOException) {
            log("主机服务绑定端口 $bindPort 失败", e)
            false
        }
    }

    /** 停止监听，并以 1001 关闭全部连接。 */
    fun stopServing() {
        if (!started) return
        started = false
        changesJob?.cancel()
        changesJob = null
        hub.closeAll(CODE_GOING_AWAY, "服务已停止")
        stop()
        log("主机服务已停止", null)
    }

    /** 更换配对码后断开全部连接：旧码立即失效，客户端需重新配对。 */
    fun kickAllConnections() {
        hub.closeAll(CODE_UNAUTHORIZED, "配对码已更换")
    }

    override fun serve(session: IHTTPSession): Response =
        if (isWebsocketRequested(session)) super.serve(session) else handleHttp(session)

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        val parameters = handshake.parameters ?: emptyMap()
        return MenuSocket(
            handshake = handshake,
            authorized = parameters["token"]?.firstOrNull() == tokenProvider(),
            requestedDeviceId = parameters["deviceId"]?.firstOrNull(),
            requestedName = parameters["name"]?.firstOrNull(),
            requestedRole = parameters["role"]?.firstOrNull()
        )
    }

    /** 非 WS 请求统一走 `/api/v1` 管线；其余路径一律 404。 */
    private fun handleHttp(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        if (!uri.startsWith(API_PREFIX)) {
            return toResponse(jsonError(404, ErrorCode.NOT_FOUND, "接口不存在"))
        }
        val path = uri.removePrefix(API_PREFIX).ifBlank { "/" }
        return try {
            if (path.trimEnd('/') == IMAGES_PATH && session.method == NanoHTTPD.Method.POST) {
                handleImageUpload(session)
            } else {
                val request = HostRequest(
                    method = session.method.name,
                    path = path,
                    query = session.parameters
                        ?.mapValues { it.value.firstOrNull().orEmpty() }
                        ?: emptyMap(),
                    headers = session.headers ?: emptyMap(),
                    body = readBody(session)
                )
                toResponse(runBlocking { api.handle(request) })
            }
        } catch (e: NanoHTTPD.ResponseException) {
            toResponse(jsonError(400, ErrorCode.INVALID_ARGUMENT, "请求体格式错误"))
        } catch (e: Exception) {
            log("主机处理 $path 失败", e)
            toResponse(jsonError(500, ErrorCode.INTERNAL, "服务器内部错误"))
        }
    }

    /**
     * JSON 请求体：优先按 `Content-Length` 从会话流读取并显式按 UTF-8 解码。
     *
     * 不依赖 `parseBody` 的 `postData`：NanoHTTPD 2.3.1 按 `Content-Type` 的 charset 解码，
     * 而契约请求头是 `application/json`（不带 charset）时会退化为 US-ASCII，中文会损坏；
     * 另外 `parseBody` 只解析 POST / PUT，PATCH 根本拿不到 `postData`。
     * 无 `Content-Length`（如 chunked）时退回 `parseBody`。
     */
    private fun readBody(session: IHTTPSession): String? {
        readRawBody(session)?.let { return it }
        val body = HashMap<String, String>()
        session.parseBody(body)
        return body["postData"]
    }

    /** 按 `Content-Length` 读取原始请求体；无长度或读取失败返回 null。 */
    private fun readRawBody(session: IHTTPSession): String? {
        val length = session.headers?.get("content-length")?.trim()?.toIntOrNull() ?: return null
        if (length <= 0) return null
        val bytes = ByteArray(length)
        var read = 0
        val stream = session.inputStream
        while (read < length) {
            val count = stream.read(bytes, read, length - read)
            if (count < 0) break
            read += count
        }
        return if (read == 0) null else String(bytes, 0, read, Charsets.UTF_8)
    }

    /**
     * `POST /images`：`file` 分区已被 NanoHTTPD 落到临时文件，先按大小与魔数校验再交给管线。
     * 临时文件由 NanoHTTPD 在请求结束时统一清理，这里不删除。
     */
    private fun handleImageUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val tempPath = files[IMAGE_FIELD]?.takeIf { it.isNotBlank() }
            ?: return toResponse(jsonError(400, ErrorCode.INVALID_ARGUMENT, "缺少 file 字段"))
        val file = File(tempPath)
        if (file.length() > MAX_IMAGE_BYTES) {
            return toResponse(jsonError(413, ErrorCode.PAYLOAD_TOO_LARGE, "图片超过 10MB"))
        }
        val bytes = file.readBytes()
        val request = HostRequest(
            method = NanoHTTPD.Method.POST.name,
            path = IMAGES_PATH,
            headers = session.headers ?: emptyMap()
        )
        val response = runBlocking {
            api.handleImageUpload(request.bearerToken, sniffImageType(bytes), bytes)
        }
        return toResponse(response)
    }

    /** multipart 分区头不暴露 Content-Type，按文件魔数判定类型，比信任客户端声明更可靠。 */
    private fun sniffImageType(bytes: ByteArray): String? = when {
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() ->
            "image/jpeg"

        bytes.size >= PNG_MAGIC.size && PNG_MAGIC.indices.all { bytes[it] == PNG_MAGIC[it] } ->
            "image/png"

        else -> null
    }

    private suspend fun broadcastChange(change: MenuChange) {
        val event = if (change is MenuChange.FullReload) {
            hub.snapshotEvent(backend.state())
        } else {
            hub.changeEvent(change) ?: return
        }
        val sent = hub.broadcast(event)
        if (sent > 0) log("已广播 ${event.type}（版本 ${event.version}）给 $sent 个连接", null)
    }

    private fun toResponse(response: HostResponse): Response {
        val status = Response.Status.lookup(response.status) ?: Response.Status.INTERNAL_ERROR
        return if (response.body.isEmpty()) {
            NanoHTTPD.newFixedLengthResponse(status, response.contentType, "")
        } else {
            NanoHTTPD.newFixedLengthResponse(
                status,
                response.contentType,
                ByteArrayInputStream(response.body),
                response.body.size.toLong()
            )
        }
    }

    private fun jsonError(status: Int, code: String, message: String): HostResponse =
        HostResponse.json(status, """{"error":{"code":"$code","message":"$message"}}""")

    /**
     * 单个 WS 连接：鉴权失败立即以 4401 关闭；成功则登记设备、发首帧快照并广播上下线。
     */
    private inner class MenuSocket(
        handshake: IHTTPSession,
        private val authorized: Boolean,
        requestedDeviceId: String?,
        requestedName: String?,
        requestedRole: String?
    ) : WebSocket(handshake), WsHub.Connection {

        override val deviceId: String =
            requestedDeviceId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

        override val name: String =
            requestedName?.takeIf { it.isNotBlank() } ?: UNNAMED_DEVICE

        override val role: String =
            requestedRole?.takeIf { it.isNotBlank() } ?: DeviceRole.CLIENT

        override fun onOpen() {
            if (!authorized) {
                log("WS 配对码校验失败，已拒绝连接", null)
                close(CODE_UNAUTHORIZED, "配对码缺失或已失效")
                return
            }
            hub.add(this)
            runBlocking {
                backend.upsertDevice(deviceId, name, role)
                val state = backend.state()
                push(hub.encode(hub.snapshotEvent(state)))
                hub.broadcast(
                    hub.deviceEvent(EventType.DEVICE_JOINED, state.version, currentDevice()),
                    except = this@MenuSocket
                )
            }
            log("客户端已连接：$name（$deviceId）", null)
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, remote: Boolean) {
            if (!authorized) return
            hub.remove(this)
            runBlocking {
                backend.touchDevice(deviceId)
                hub.broadcast(
                    hub.deviceEvent(EventType.DEVICE_LEFT, backend.currentVersion(), currentDevice())
                )
            }
            log("客户端已断开：$name（$deviceId）", null)
        }

        override fun onMessage(message: WebSocketFrame) = Unit

        override fun onPong(pong: WebSocketFrame) = Unit

        override fun onException(exception: IOException) {
            hub.remove(this)
            log("WS 连接异常：$deviceId", exception)
        }

        override fun push(frame: String): Boolean = runCatching { send(frame) }.isSuccess

        override fun close(code: Int, reason: String) {
            runCatching { sendFrame(closeFrame(code, reason)) }
        }

        /** NanoWSD 的 `CloseCode` 枚举不含 4401，按协议手工构造关闭帧（2 字节大端码 + UTF-8 原因）。 */
        private fun closeFrame(code: Int, reason: String): WebSocketFrame {
            val reasonBytes = reason.toByteArray(Charsets.UTF_8)
            val payload = ByteArray(2 + reasonBytes.size)
            payload[0] = ((code ushr 8) and 0xFF).toByte()
            payload[1] = (code and 0xFF).toByte()
            reasonBytes.copyInto(payload, 2)
            return WebSocketFrame(WebSocketFrame.OpCode.Close, true, payload)
        }

        private fun currentDevice(): DeviceDto =
            DeviceDto(deviceId, name, role, Iso8601.format(System.currentTimeMillis()))
    }

    companion object {
        /** 契约路径前缀。 */
        private const val API_PREFIX = "/api/v1"

        /** 图片路径（含上传）。 */
        private const val IMAGES_PATH = "/images"

        /** 图片上传的 multipart 字段名。 */
        private const val IMAGE_FIELD = "file"

        /** 单张图片上限。 */
        private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

        /** 连接空闲读超时（毫秒）。 */
        private const val READ_TIMEOUT_MS = 5000

        /** 未提供设备名时的占位名。 */
        private const val UNNAMED_DEVICE = "未命名设备"

        /** WS 关闭码：配对码缺失或已失效。 */
        const val CODE_UNAUTHORIZED = 4401

        /** WS 关闭码：服务端主动关闭。 */
        private const val CODE_GOING_AWAY = 1001

        /** 端口顺延次数（8080 → 8081 → 8082）。 */
        const val PORT_FALLBACK_COUNT = 3

        private val PNG_MAGIC = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

        /**
         * 依次尝试 [preferredPort] 起的连续端口，返回首个绑定成功的实例；全部被占用返回 null。
         * 失败实例随即释放，不影响下一次尝试。
         */
        fun start(
            api: HostApi,
            backend: RepositoryMenuBackend,
            repository: MenuRepository,
            tokenProvider: () -> String,
            scope: CoroutineScope,
            log: (String, Throwable?) -> Unit,
            preferredPort: Int
        ): HostServer? {
            for (offset in 0 until PORT_FALLBACK_COUNT) {
                val server = HostServer(
                    api = api,
                    backend = backend,
                    repository = repository,
                    tokenProvider = tokenProvider,
                    scope = scope,
                    log = log,
                    bindPort = preferredPort + offset
                )
                if (server.startServing()) return server
                runCatching { server.stop() }
            }
            return null
        }
    }
}