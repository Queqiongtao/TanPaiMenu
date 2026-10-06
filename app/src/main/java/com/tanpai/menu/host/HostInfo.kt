package com.tanpai.menu.host

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.Inet4Address
import java.util.Locale

/**
 * 主机配对信息：局域网 IPv4、访问地址与二维码（`docs/design/0015` 第 2.7 节）。
 *
 * 纯拼接逻辑（[address] / [qrContent]）不触达 Android 能力，可在 JVM 单测直接验证；
 * 取 IP 与生成二维码位图依赖设备。
 */
object HostInfo {

    /** 拼访问地址 `http://<ip>:<port>`；[ip] 为空返回 null。 */
    fun address(ip: String?, port: Int): String? =
        ip?.takeIf { it.isNotBlank() }?.let { "http://$it:$port" }

    /** 二维码内容 `http://<ip>:<port>/#pair=<token>`，客户端扫码后按该 token 配对。 */
    fun qrContent(ip: String?, port: Int, token: String): String? =
        address(ip, port)?.let { "$it/#pair=$token" }

    /** 取当前局域网 IPv4 地址；无可用网络或权限缺失时返回 null。 */
    fun lanIpv4(context: Context): String? = runCatching {
        ipFromConnectivity(context) ?: ipFromWifi(context)
    }.getOrNull()

    /** zxing 生成二维码位图；[sizePx] 为边长像素，失败返回 null。 */
    fun qrBitmap(content: String, sizePx: Int): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                pixels[y * sizePx + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    /** 首选 `ConnectivityManager` 的链路地址，可避开已关闭但被缓存的 Wi-Fi 信息。 */
    private fun ipFromConnectivity(context: Context): String? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val properties = manager.activeNetwork?.let { manager.getLinkProperties(it) } ?: return null
        return properties.linkAddresses
            .asSequence()
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    /** 兜底：`WifiManager` 的整型 IP（小端存储，按字节还原）。 */
    @Suppress("DEPRECATION")
    private fun ipFromWifi(context: Context): String? {
        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        val ip = manager.connectionInfo?.ipAddress ?: 0
        if (ip == 0) return null
        return String.format(
            Locale.US,
            "%d.%d.%d.%d",
            ip and 0xFF,
            ip shr 8 and 0xFF,
            ip shr 16 and 0xFF,
            ip shr 24 and 0xFF
        )
    }
}