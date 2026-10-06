package com.tanpai.menu.host

import android.graphics.Bitmap
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.data.db.TpDatabase
import com.tanpai.menu.imaging.ImageStore
import com.tanpai.menu.net.protocol.DishDto
import com.tanpai.menu.net.protocol.EventType
import com.tanpai.menu.net.protocol.ImageUploadDto
import com.tanpai.menu.net.protocol.ProtocolJson
import com.tanpai.menu.net.protocol.StateDto
import com.tanpai.menu.net.protocol.WireEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.net.URLEncoder

/**
 * 真机 / 模拟器上用 `HttpURLConnection` 与裸 Socket 验收主机服务（`docs/design/0015` 第 5 节）：
 * 鉴权、快照、幂等重放、PATCH、卖出/撤销、设备列表、图片上传与读取、WS 首帧与一次广播。
 */
@RunWith(AndroidJUnit4::class)
class HostServerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val token = "test-token"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var db: TpDatabase
    private lateinit var repository: MenuRepository
    private lateinit var imageStore: ImageStore
    private lateinit var server: HostServer
    private var categoryId = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TpDatabase::class.java
        ).build()
        repository = MenuRepository(db, now = { 1000L })
        imageStore = ImageStore(context)
        val backend = RepositoryMenuBackend(
            repository = repository,
            imageStore = imageStore,
            deviceDao = db.deviceDao(),
            now = { 2000L }
        )
        server = requireNotNull(
            HostServer.start(
                api = HostApi(backend, { token }),
                backend = backend,
                repository = repository,
                tokenProvider = { token },
                scope = scope,
                logInfo = { },
                logError = { _, _ -> },
                preferredPort = TEST_PORT
            )
        ) { "主机服务未能绑定到 $TEST_PORT 起的端口" }
        runBlocking {
            categoryId = repository.createCategory("热菜", 1)
            repository.createDish(
                Dish(
                    categoryId = categoryId,
                    name = "烤串",
                    priceCents = 300,
                    stockEnabled = true,
                    stockCount = 5,
                    updatedAt = 0L
                )
            )
        }
    }

    @After
    fun tearDown() {
        server.stopServing()
        scope.cancel()
        db.close()
    }

    @Test
    fun stateRequiresBearerToken() {
        val (status, body) = http("GET", "/api/v1/state", bearer = null)
        assertEquals(401, status)
        assertTrue(body.contains("unauthorized"))
    }

    @Test
    fun stateReturnsEnabledMenuSnapshot() {
        val (status, body) = http("GET", "/api/v1/state")
        assertEquals(200, status)
        val state = ProtocolJson.decodeFromString(StateDto.serializer(), body)
        assertEquals(1, state.categories.size)
        assertEquals(1, state.dishes.size)
        assertEquals("烤串", state.dishes.first().name)
    }

    @Test
    fun createDishIsIdempotentPerRequestId() {
        val payload =
            """{"categoryId":$categoryId,"name":"烤韭菜","priceCents":1200,"requestId":"r-1"}"""

        val first = http("POST", "/api/v1/dishes", payload)
        val second = http("POST", "/api/v1/dishes", payload)

        assertEquals(201, first.first)
        assertEquals(first.second, second.second)
        // 中文必须原样往返：NanoHTTPD 默认按 US-ASCII 解 POST 体，曾导致中文变成替换字符。
        assertTrue(first.second.contains("烤韭菜"))
        assertEquals(2, runBlocking { repository.enabledDishes() }.size)

        val (_, stateBody) = http("GET", "/api/v1/state")
        assertTrue(stateBody.contains("烤韭菜"))
    }

    @Test
    fun patchDishUpdatesNameAndPrice() {
        val dishId = runBlocking { repository.enabledDishes() }.first().id
        val (status, body) = http(
            "PATCH",
            "/api/v1/dishes/$dishId",
            """{"name":"烤羊肉","priceCents":500}"""
        )
        assertEquals(200, status)
        val dish = ProtocolJson.decodeFromString(DishDto.serializer(), body)
        assertEquals("烤羊肉", dish.name)
        assertEquals(500, dish.priceCents)
    }

    @Test
    fun sellReplayKeepsStockAndUnsellRestores() {
        val dishId = runBlocking { repository.enabledDishes() }.first().id
        val sellBody = """{"deviceId":"phone-a","requestId":"s-1"}"""

        val (sellStatus, sellResponse) = http("POST", "/api/v1/dishes/$dishId/sell", sellBody)
        assertEquals(200, sellStatus)
        assertEquals(4, ProtocolJson.decodeFromString(DishDto.serializer(), sellResponse).stockCount)

        val (replayStatus, replayResponse) = http("POST", "/api/v1/dishes/$dishId/sell", sellBody)
        assertEquals(200, replayStatus)
        assertEquals(sellResponse, replayResponse)

        val (unsellStatus, unsellResponse) = http(
            "POST",
            "/api/v1/dishes/$dishId/unsell",
            """{"deviceId":"phone-a","requestId":"u-1"}"""
        )
        assertEquals(200, unsellStatus)
        assertEquals(5, ProtocolJson.decodeFromString(DishDto.serializer(), unsellResponse).stockCount)
    }

    @Test
    fun devicesListContainsConnectedWebSocketClient() {
        WsClient("127.0.0.1", server.port, "/ws?token=$token&deviceId=phone-a&name=${urlEncode("测试机")}&role=client").use { client ->
            client.connect()
            client.readTextFrame()

            val (status, body) = http("GET", "/api/v1/devices")
            assertEquals(200, status)
            assertTrue(body.contains("phone-a"))
            assertTrue(body.contains("测试机"))
        }
    }

    @Test
    fun imageUploadThenReadRoundTrips() {
        val (uploadStatus, uploadBody) = uploadImage(pngBytes())
        assertEquals(201, uploadStatus)
        val uploaded = ProtocolJson.decodeFromString(ImageUploadDto.serializer(), uploadBody)
        assertTrue(uploaded.imagePath.endsWith(".jpg"))
        assertEquals("/api/v1/${uploaded.imagePath}", uploaded.url)

        val name = uploaded.imagePath.substringAfterLast('/')
        val (readStatus, bytes) = httpBytes("GET", "/api/v1/images/$name")
        assertEquals(200, readStatus)
        assertTrue(bytes.isNotEmpty())

        imageStore.delete(uploaded.imagePath)
    }

    @Test
    fun webSocketSendsSnapshotThenDishUpdated() {
        WsClient("127.0.0.1", server.port, "/ws?token=$token&deviceId=phone-ws&name=${urlEncode("测试机")}&role=client").use { client ->
            client.connect()

            val snapshot = ProtocolJson.decodeFromString(
                WireEvent.serializer(),
                client.readTextFrame()
            )
            assertEquals(EventType.SNAPSHOT, snapshot.type)

            val dishId = runBlocking { repository.enabledDishes() }.first().id
            val (patchStatus, _) = http("PATCH", "/api/v1/dishes/$dishId", """{"name":"烤羊肉"}""")
            assertEquals(200, patchStatus)

            val updated = ProtocolJson.decodeFromString(
                WireEvent.serializer(),
                client.readTextFrame()
            )
            assertEquals(EventType.DISH_UPDATED, updated.type)
            assertEquals("烤羊肉", updated.payload["name"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun webSocketWithWrongTokenIsClosed() {
        WsClient("127.0.0.1", server.port, "/ws?token=wrong-token&deviceId=phone-b").use { client ->
            client.connect()
            assertTrue(client.readFrameOpcode() == OPCODE_CLOSE)
        }
    }

    private fun http(
        method: String,
        path: String,
        body: String? = null,
        bearer: String? = token
    ): Pair<Int, String> {
        val connection = open(method, path, bearer)
        return try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            status to readBody(connection, status)
        } finally {
            connection.disconnect()
        }
    }

    private fun httpBytes(method: String, path: String): Pair<Int, ByteArray> {
        val connection = open(method, path, token)
        return try {
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            status to (stream?.use { it.readBytes() } ?: ByteArray(0))
        } finally {
            connection.disconnect()
        }
    }

    private fun uploadImage(bytes: ByteArray): Pair<Int, String> {
        val boundary = "----tanpai-test-boundary"
        val connection = open("POST", "/api/v1/images", token)
        return try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.outputStream.use { out ->
                out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                out.write(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"dish.png\"\r\n"
                        .toByteArray(Charsets.UTF_8)
                )
                out.write("Content-Type: image/png\r\n\r\n".toByteArray(Charsets.UTF_8))
                out.write(bytes)
                out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            status to readBody(connection, status)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(method: String, path: String, bearer: String?): HttpURLConnection {
        val connection = URL("http://127.0.0.1:${server.port}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        return connection
    }

    private fun readBody(connection: HttpURLConnection, status: Int): String {
        val stream = if (status >= 400) connection.errorStream else connection.inputStream
        return stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
    }

    /** 非 ASCII 查询参数必须百分号编码，否则套接字按 ASCII 写出会丢字。 */
    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF3366FF.toInt())
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    /** 极简 WebSocket 客户端：只做握手与读取服务端文本 / 关闭帧，够验收用。 */
    private class WsClient(private val host: String, private val port: Int, private val path: String) : Closeable {

        private val socket = Socket(host, port)
        private val input = socket.getInputStream()
        private val output = socket.getOutputStream()

        fun connect() {
            val key = Base64.encodeToString(ByteArray(16) { it.toByte() }, Base64.NO_WRAP)
            val request = buildString {
                append("GET $path HTTP/1.1\r\n")
                append("Host: $host:$port\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: $key\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                append("\r\n")
            }
            output.write(request.toByteArray(Charsets.US_ASCII))
            output.flush()

            val header = StringBuilder()
            while (!header.endsWith("\r\n\r\n")) {
                val byte = input.read()
                if (byte < 0) throw IOException("握手期间连接被关闭")
                header.append(byte.toChar())
            }
            if (!header.startsWith("HTTP/1.1 101")) {
                throw IOException("握手失败：${header.lineSequence().first()}")
            }
        }

        /** 读取一帧（服务端帧不带掩码），返回 opcode 与载荷。 */
        private fun readFrame(): Pair<Int, ByteArray> {
            val first = input.read()
            val second = input.read()
            if (first < 0 || second < 0) throw IOException("连接已关闭")
            val opcode = first and 0x0F
            var length = (second and 0x7F).toLong()
            if (length == 126L) {
                length = ((input.read() shl 8) or input.read()).toLong()
            } else if (length == 127L) {
                length = 0
                repeat(8) { length = (length shl 8) or input.read().toLong() }
            }
            val payload = ByteArray(length.toInt())
            var read = 0
            while (read < payload.size) {
                val count = input.read(payload, read, payload.size - read)
                if (count < 0) throw IOException("帧读取中断")
                read += count
            }
            return opcode to payload
        }

        fun readTextFrame(): String {
            while (true) {
                val (opcode, payload) = readFrame()
                // 服务端会定期发心跳 ping，读取时跳过控制帧（docs/design/0016 第 2.4 节）。
                if (opcode == OPCODE_PING || opcode == OPCODE_PONG) continue
                check(opcode == OPCODE_TEXT) { "期望文本帧，实际 opcode=$opcode" }
                return String(payload, Charsets.UTF_8)
            }
        }

        fun readFrameOpcode(): Int = readFrame().first

        override fun close() {
            runCatching { socket.close() }
        }
    }

    private companion object {
        const val TEST_PORT = 18080
        const val TIMEOUT_MS = 5_000
        const val OPCODE_TEXT = 0x1
        const val OPCODE_PING = 0x9
        const val OPCODE_PONG = 0xA
        const val OPCODE_CLOSE = 0x8
    }
}