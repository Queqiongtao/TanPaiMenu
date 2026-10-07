package com.tanpai.menu.net

/**
 * 配对二维码内容解析（`docs/design/0022` 第 3 节）。
 *
 * 主机生成的二维码内容为 `http://<ip>:<port>/#pair=<长期令牌>`（`HostInfo.qrContent`），
 * 手机扫到后直接落盘地址与令牌、跳过 `POST /pair`。为兼容手工生成的二维码，这里同时接受
 * 裸地址（`http://<ip>:<port>`）与纯令牌写法；解析不触达任何 Android 能力，可 JVM 单测直接验证。
 */
object PairQr {

    /** 解析成功得到的主机地址与令牌；[hostAddress] 已规范化，[token] 为原始大小写。 */
    data class Target(val hostAddress: String, val token: String)

    /** 解析结果；[Failed.reason] 为可直接展示给用户的中文提示。 */
    sealed interface Result {
        data class Parsed(val target: Target) : Result

        data class Failed(val reason: String) : Result
    }

    /** 令牌参数名，与主机 `HostInfo.qrContent` 的 `#pair=` 一致。 */
    private const val TOKEN_PARAM = "pair"

    /** 令牌最小长度：主机为 64 位十六进制，留出余量以兼容更短的测试令牌。 */
    private const val MIN_TOKEN_LENGTH = 16

    /** 十六进制令牌形状校验。 */
    private val TOKEN_PATTERN = Regex("^[0-9a-fA-F]+$")

    private const val NO_TOKEN = "二维码中没有配对令牌，请扫描平板上的配对二维码"
    private const val BAD_CONTENT = "无法识别的内容，请扫描平板上的配对二维码"

    /**
     * 解析扫描到的二维码文本。
     *
     * 支持「带令牌的完整地址」与「不带令牌的完整地址」两种写法；其余内容判为无效
     * （其中包括只含令牌、不含地址的二维码，缺少地址无法连接）。
     */
    fun parse(raw: String): Result {
        val text = raw.trim()
        if (text.isEmpty()) return Result.Failed(BAD_CONTENT)

        val candidate = text.substringBefore('#')
        val fragment = text.substringAfter('#', "")
        val tokenFromFragment = tokenIn(fragment)
        val tokenFromQuery = tokenIn(candidate.substringAfter('?', ""))

        if (!looksLikeAddress(candidate)) return Result.Failed(BAD_CONTENT)

        val hostAddress = HostAddress.normalize(candidate)
            ?: return Result.Failed("二维码中的平板地址无效，请重新扫描")
        val token = tokenFromFragment ?: tokenFromQuery ?: return Result.Failed(NO_TOKEN)
        if (!isToken(token)) return Result.Failed(NO_TOKEN)
        return Result.Parsed(Target(hostAddress, token))
    }

    /** 内容是否看起来是「地址」：带 scheme、带端口或形如 IPv4 / 主机名。 */
    private fun looksLikeAddress(text: String): Boolean {
        if (text.isEmpty()) return false
        if (text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true)
        ) {
            return true
        }
        val hostPart = text.substringBefore('/')
        // 带端口，或形如 IPv4 / 主机名（含点、由字母数字与 . - _ 组成），其余一律视为无关内容。
        if (hostPart.contains(':')) return true
        return Regex("^[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$").matches(hostPart) &&
            hostPart.contains('.')
    }

    /** 从 `pair=<token>` 形式的参数串里取出令牌值。 */
    private fun tokenIn(params: String): String? {
        if (params.isEmpty()) return null
        return params
            .split('&')
            .firstOrNull { it.substringBefore('=').trim() == TOKEN_PARAM }
            ?.substringAfter('=', "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun isToken(value: String): Boolean =
        value.length >= MIN_TOKEN_LENGTH && TOKEN_PATTERN.matches(value)
}
