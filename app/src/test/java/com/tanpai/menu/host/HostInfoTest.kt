package com.tanpai.menu.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 配对地址与二维码内容的纯拼接逻辑（不触达 Android 能力）。 */
class HostInfoTest {

    @Test
    fun addressCombinesIpAndPort() {
        assertEquals("http://192.168.1.5:8080", HostInfo.address("192.168.1.5", 8080))
    }

    @Test
    fun addressIsNullWithoutIp() {
        assertNull(HostInfo.address(null, 8080))
        assertNull(HostInfo.address("   ", 8080))
    }

    @Test
    fun qrContentCarriesPairToken() {
        assertEquals(
            "http://192.168.1.5:8080/#pair=abc123",
            HostInfo.qrContent("192.168.1.5", 8080, "abc123")
        )
    }

    @Test
    fun qrContentIsNullWithoutIp() {
        assertNull(HostInfo.qrContent(null, 8080, "abc123"))
    }
}