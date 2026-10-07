package com.tanpai.menu.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 配对二维码解析（`docs/design/0022` 第 3 节、第 5 节）。 */
class PairQrTest {

    private val token = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun parsesHostQrContent() {
        val result = PairQr.parse("http://192.168.51.177:8080/#pair=$token")

        val target = (result as PairQr.Result.Parsed).target
        assertEquals("http://192.168.51.177:8080", target.hostAddress)
        assertEquals(token, target.token)
    }

    @Test
    fun parsesQueryFormAndKeepsTokenCase() {
        val upper = token.uppercase()
        val result = PairQr.parse("http://192.168.1.5:8080/?pair=$upper")

        val target = (result as PairQr.Result.Parsed).target
        assertEquals("http://192.168.1.5:8080", target.hostAddress)
        assertEquals("令牌按原文保存", upper, target.token)
    }

    @Test
    fun trimsSurroundingWhitespaceAndNewlines() {
        val result = PairQr.parse("  \n http://192.168.1.5:8080/#pair=$token \n ")

        val target = (result as PairQr.Result.Parsed).target
        assertEquals("http://192.168.1.5:8080", target.hostAddress)
        assertEquals(token, target.token)
    }

    @Test
    fun acceptsExtraFragmentParameters() {
        val result = PairQr.parse("http://192.168.1.5:8080/#a=1&pair=$token&b=2")

        val target = (result as PairQr.Result.Parsed).target
        assertEquals(token, target.token)
    }

    @Test
    fun fillsDefaultPortForBareHost() {
        val result = PairQr.parse("http://192.168.1.5/#pair=$token")

        val target = (result as PairQr.Result.Parsed).target
        assertEquals("http://192.168.1.5:${HostAddress.DEFAULT_PORT}", target.hostAddress)
    }

    @Test
    fun reportsMissingTokenForAddressOnly() {
        val result = PairQr.parse("http://192.168.1.5:8080")

        val failure = result as PairQr.Result.Failed
        assertTrue(failure.reason, failure.reason.contains("配对令牌"))
    }

    @Test
    fun reportsMissingTokenForEmptyTokenValue() {
        val result = PairQr.parse("http://192.168.1.5:8080/#pair=")

        assertTrue((result as PairQr.Result.Failed).reason.contains("配对令牌"))
    }

    @Test
    fun reportsInvalidAddressForIllegalHost() {
        val result = PairQr.parse("http://192.168.1.5:70000/#pair=$token")

        assertTrue((result as PairQr.Result.Failed).reason.contains("地址无效"))
    }

    @Test
    fun reportsInvalidAddressForOutOfRangePortText() {
        val result = PairQr.parse("http://192.168.1.5:abc/#pair=$token")

        assertTrue((result as PairQr.Result.Failed).reason.contains("地址无效"))
    }

    @Test
    fun rejectsForeignQrWithoutPairToken() {
        val result = PairQr.parse("https://weixin.qq.com/r/abcdef")

        // 外部二维码（微信码等）不是本系统内容：识别为「没有配对令牌」而不是当成主机地址去连。
        assertTrue((result as PairQr.Result.Failed).reason.contains("配对令牌"))
    }

    @Test
    fun rejectsUnrelatedTextContent() {
        val result = PairQr.parse("HELLO-WORLD")

        assertTrue((result as PairQr.Result.Failed).reason.contains("无法识别"))
    }

    @Test
    fun rejectsBareTokenWithoutAddress() {
        val result = PairQr.parse(token)

        assertTrue((result as PairQr.Result.Failed).reason.contains("无法识别"))
    }

    @Test
    fun rejectsEmptyContent() {
        val result = PairQr.parse("   ")

        assertTrue((result as PairQr.Result.Failed).reason.contains("无法识别"))
    }

    @Test
    fun rejectsTooShortToken() {
        val result = PairQr.parse("http://192.168.1.5:8080/#pair=abc123")

        assertTrue((result as PairQr.Result.Failed).reason.contains("配对令牌"))
    }

    @Test
    fun rejectsNonHexToken() {
        val result = PairQr.parse("http://192.168.1.5:8080/#pair=zzzzzzzzzzzzzzzzzzzz")

        assertTrue((result as PairQr.Result.Failed).reason.contains("配对令牌"))
    }
}
