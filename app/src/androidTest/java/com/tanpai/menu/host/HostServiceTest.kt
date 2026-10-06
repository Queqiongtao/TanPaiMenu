package com.tanpai.menu.host

import android.app.NotificationManager
import android.util.Base64
import com.tanpai.menu.net.protocol.DeviceRole
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanpai.menu.App
import com.tanpai.menu.data.SettingsStore
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 前台服务保活与开关验收（`docs/design/0016` 第 5 节）：
 * 启用后服务端在监听且常驻通知存在；关闭后停止监听并移除通知。
 */
@RunWith(AndroidJUnit4::class)
class HostServiceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = ApplicationProvider.getApplicationContext<App>()
    private val settings = SettingsStore(context)

    @Before
    fun setUp() {
        settings.hostEnabled = true
        HostService.start(context)
        assertTrue("启用后主机服务未在监听", waitUntil { app.container.hostServer?.isServing == true })
    }

    @After
    fun tearDown() {
        settings.hostEnabled = true
        HostService.start(context)
        waitUntil { app.container.hostServer?.isServing == true }
    }

    @Test
    fun enablingKeepsForegroundServiceAndNotification() {
        assertTrue("未看到手机控制端的常驻通知", waitUntil { hasHostNotification() })
        assertTrue("服务运行态与实例不一致", app.container.hostStatus.running)
    }

    @Test
    fun disablingStopsServerAndRemovesNotification() {
        app.container.hostStatus.setHostEnabled(false)

        assertTrue("关闭后主机服务仍在监听", waitUntil { app.container.hostServer == null })
        assertTrue("关闭后运行态仍为真", waitUntil { !app.container.hostStatus.running })
        assertTrue("关闭后常驻通知仍在", waitUntil { !hasHostNotification() })
    }

    @Test
    fun onlineDevicesTrackWebSocketConnections() {
        val port = app.container.hostServer?.port ?: 0
        assertTrue("主机服务未在监听", port > 0)
        val token = app.container.settingsStore.pairToken
        val socket = Socket("127.0.0.1", port)
        try {
            val request = buildString {
                append("GET /ws?token=$token&deviceId=$DEVICE_ID&name=${urlEncode(DEVICE_NAME)}&role=${DeviceRole.CLIENT} HTTP/1.1\r\n")
                append("Host: 127.0.0.1:$port\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: ${Base64.encodeToString(ByteArray(16) { it.toByte() }, Base64.NO_WRAP)}\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                append("\r\n")
            }
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            val header = readHeader(socket.getInputStream())
            assertTrue("WS 握手失败：$header", header.startsWith("HTTP/1.1 101"))

            assertTrue(
                "在线设备未包含刚连接的手机",
                waitUntil { app.container.hostStatus.onlineDevices().any { it.deviceId == DEVICE_ID } }
            )
            assertEquals(
                DEVICE_NAME,
                app.container.hostStatus.onlineDevices().first { it.deviceId == DEVICE_ID }.name
            )
        } finally {
            socket.close()
        }

        assertTrue("断开后在线设备未清空", waitUntil { app.container.hostStatus.onlineDevices().isEmpty() })
    }

    private fun readHeader(input: InputStream): String {
        val header = StringBuilder()
        while (!header.endsWith("\r\n\r\n")) {
            val byte = input.read()
            if (byte < 0) throw IOException("握手期间连接被关闭")
            header.append(byte.toChar())
        }
        return header.toString()
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun hasHostNotification(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.activeNotifications.any { it.packageName == context.packageName }
    }

    private fun waitUntil(timeoutMs: Long = 10_000L, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100L)
        }
        return condition()
    }

    private companion object {
        const val DEVICE_ID = "test-phone"
        const val DEVICE_NAME = "验收手机"
    }
}