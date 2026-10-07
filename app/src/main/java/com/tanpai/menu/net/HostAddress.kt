package com.tanpai.menu.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 主机地址解析与 URL 拼接（`docs/design/0019` 第 2.2 节）。
 *
 * 允许三种写法：`192.168.1.5:8080`、`192.168.1.5`（补默认端口）、`http://192.168.1.5:8080`。
 * 只支持局域网 IPv4 / 主机名，不支持 IPv6 与路径前缀。
 */
object HostAddress {

    /** 默认端口，与主机 `SettingsStore.serverPort` 的默认值一致。 */
    const val DEFAULT_PORT = 8080

    /** 主机名 / IPv4 字面量。 */
    private val HOST_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    /** 规范化成 `http://host:port`；非法输入返回 null。 */
    fun normalize(input: String): String? = baseUrl(input)?.toString()?.removeSuffix("/")

    /** 事件订阅地址 `ws://host:port/ws?token=...&deviceId=...&name=...&role=client`。 */
    fun webSocketUrl(input: String, token: String, deviceId: String, name: String): String? {
        val base = baseUrl(input) ?: return null
        return base.newBuilder()
            .addPathSegment("ws")
            .addQueryParameter("token", token)
            .addQueryParameter("deviceId", deviceId)
            .addQueryParameter("name", name)
            .addQueryParameter("role", "client")
            .build()
            .toString()
            .replaceFirst("http", "ws")
    }

    /** 菜品图片地址：`imagePath` 形如 `images/<uuid>.jpg`，契约前缀为 `/api/v1`。 */
    fun imageUrl(input: String, imagePath: String): String? =
        normalize(input)?.let { "$it/api/v1/$imagePath" }

    /** 解析用户输入为 http 基地址；非法返回 null。 */
    fun baseUrl(input: String): HttpUrl? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val withoutScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring("http://".length)
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring("https://".length)
            else -> trimmed
        }
        val hostPart = withoutScheme.substringBefore("/")
        val host = hostPart.substringBefore(":").trim()
        val portText = hostPart.substringAfter(":", "")
        val port = if (portText.isBlank()) DEFAULT_PORT else portText.toIntOrNull() ?: return null
        if (host.isEmpty() || !HOST_PATTERN.matches(host)) return null
        if (port !in 1..65535) return null
        return "http://$host:$port".toHttpUrlOrNull()
    }
}