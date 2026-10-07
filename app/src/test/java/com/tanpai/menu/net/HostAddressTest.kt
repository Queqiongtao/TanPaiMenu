package com.tanpai.menu.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 主机地址解析与 URL 拼接单测（`docs/design/0019` 第 2.2 节）。 */
class HostAddressTest {

    @Test
    fun ipWithPortIsNormalized() {
        assertEquals("http://192.168.1.5:8080", HostAddress.normalize("192.168.1.5:8080"))
    }

    @Test
    fun missingPortUsesDefault() {
        assertEquals("http://192.168.1.5:8080", HostAddress.normalize("192.168.1.5"))
    }

    @Test
    fun httpSchemeAndWhitespaceAreAccepted() {
        assertEquals("http://192.168.1.5:9090", HostAddress.normalize("  http://192.168.1.5:9090/ "))
    }

    @Test
    fun hostNameIsAccepted() {
        assertEquals("http://tablet.local:8080", HostAddress.normalize("tablet.local"))
    }

    @Test
    fun illegalInputReturnsNull() {
        assertNull(HostAddress.normalize(""))
        assertNull(HostAddress.normalize("   "))
        assertNull(HostAddress.normalize("http://"))
        assertNull(HostAddress.normalize("192.168.1.5:70000"))
        assertNull(HostAddress.normalize("192.168.1.5:abc"))
        assertNull(HostAddress.normalize("-bad-host-"))
    }

    @Test
    fun webSocketUrlCarriesCredentials() {
        val url = HostAddress.webSocketUrl(
            input = "192.168.1.5:8080",
            token = "tok",
            deviceId = "dev-1",
            name = "手机A"
        )
        assertEquals(
            "ws://192.168.1.5:8080/ws?token=tok&deviceId=dev-1&name=%E6%89%8B%E6%9C%BAA&role=client",
            url
        )
    }

    @Test
    fun imageUrlUsesApiPrefix() {
        assertEquals(
            "http://192.168.1.5:8080/api/v1/images/a.jpg",
            HostAddress.imageUrl("192.168.1.5:8080", "images/a.jpg")
        )
        assertNull(HostAddress.imageUrl("bad host", "images/a.jpg"))
    }
}