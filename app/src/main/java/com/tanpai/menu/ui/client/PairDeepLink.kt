package com.tanpai.menu.ui.client

import android.content.Intent
import android.net.Uri

/**
 * 配对深链接解析（`docs/design/0022` 第 2.3 节）。
 *
 * 支持的写法：
 * - `tanpai://pair?content=<二维码原文>`；带 `address` 与 `token` 参数时直接拼出二维码原文；
 * - `tanpai://pair?...` 之外的参数一律忽略。
 *
 * 同时接受二维码里的原始 `http://<ip>:<port>/#pair=<token>`：系统把它交给本应用时
 * （Manifest 已声明该 intent-filter），客户端按同一路径配对。
 */
object PairDeepLink {

    private const val SCHEME = "tanpai"
    private const val HOST = "pair"
    private const val PARAM_CONTENT = "content"
    private const val PARAM_ADDRESS = "address"
    private const val PARAM_TOKEN = "token"
    private const val FRAGMENT_MARK = "#pair="

    /** 从 Intent 解析出待配对的二维码原文；不是配对链接返回 null。 */
    fun contentFrom(intent: Intent?): String? {
        if (intent == null) return null
        val data = intent.data ?: return null
        return contentOf(data)
    }

    /** 解析 `Uri`；不是配对链接返回 null。 */
    fun contentOf(data: Uri): String? = resolve(
        scheme = data.scheme,
        host = data.host,
        text = data.toString(),
        content = data.getQueryParameter(PARAM_CONTENT),
        address = data.getQueryParameter(PARAM_ADDRESS),
        token = data.getQueryParameter(PARAM_TOKEN)
    )

    /**
     * 纯逻辑解析（可在 JVM 单测直接验证，不触达 `android.net.Uri`）。
     *
     * - `tanpai://pair?content=<二维码原文>`：直接返回原文；
     * - `tanpai://pair?address=<ip:port>&token=<token>`：拼成二维码原文；
     * - `http(s)://...#pair=<token>`：返回整串（二维码扫出来的就是这个）；
     * - 其余返回 null。
     */
    fun resolve(
        scheme: String?,
        host: String?,
        text: String,
        content: String? = null,
        address: String? = null,
        token: String? = null
    ): String? {
        return when (scheme?.lowercase()) {
            SCHEME -> {
                if (host != HOST) return null
                content?.takeIf { it.isNotBlank() }?.let { return it }
                val addr = address?.takeIf { it.isNotBlank() } ?: return null
                val secret = token?.takeIf { it.isNotBlank() } ?: return null
                "http://$addr/$FRAGMENT_MARK$secret"
            }

            "http", "https" -> text.takeIf { it.contains(FRAGMENT_MARK) }

            else -> null
        }
    }
}
