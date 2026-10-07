package com.tanpai.menu.ui.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 配对深链接解析单测（`docs/design/0022` 第 2.3 节）。
 *
 * 只测纯逻辑 [PairDeepLink.resolve]：`android.net.Uri` 在 JVM 单测里是空实现，不能直接构造。
 */
class PairDeepLinkTest {

    private val token = "0123456789abcdef0123456789abcdef"

    @Test
    fun readsContentParameter() {
        val content = "http://192.168.1.5:8080/#pair=$token"

        assertEquals(
            content,
            PairDeepLink.resolve(
                scheme = "tanpai",
                host = "pair",
                text = "tanpai://pair?content=$content",
                content = content
            )
        )
    }

    @Test
    fun buildsContentFromAddressAndToken() {
        assertEquals(
            "http://192.168.1.5:8080/#pair=$token",
            PairDeepLink.resolve(
                scheme = "tanpai",
                host = "pair",
                text = "tanpai://pair?address=192.168.1.5:8080&token=$token",
                address = "192.168.1.5:8080",
                token = token
            )
        )
    }

    @Test
    fun acceptsHttpQrContent() {
        val content = "http://192.168.1.5:8080/#pair=$token"

        assertEquals(content, PairDeepLink.resolve("http", "192.168.1.5", content))
    }

    @Test
    fun rejectsUnrelatedLinks() {
        assertNull(PairDeepLink.resolve("tanpai", "other", "tanpai://other?content=x", content = "x"))
        assertNull(PairDeepLink.resolve("http", "example.com", "http://example.com/hello"))
        assertNull(PairDeepLink.resolve("tanpai", "pair", "tanpai://pair", address = null, token = null))
        assertNull(PairDeepLink.resolve(null, null, ""))
    }
}
