package com.tanpai.menu.qrcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * 二维码解码（`docs/design/0022` 第 2.2 节）。
 *
 * 只做「亮度数组 → 文本」这一段，不触达相机与 Android 组件；解码库为已在类路径上的
 * `com.google.zxing:core`，不引入 `zxing-android-embedded`（避免 Activity 依赖）。
 * 相机帧的 Y 平面本身就是灰度数据，因此直接构造 [PlanarYUVLuminanceSource]，不需要 YUV→RGB 转换。
 */
object QrDecoder {

    /** 单帧内的尝试顺序：先专用二维码解码器（带纯二维码提示，容错稍好），再兜底多格式。 */
    private val hints: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    /**
     * 解码一帧灰度数据。
     *
     * [luminance] 为紧凑排列的行优先灰度数组（长度 = [width] × [height]），由调用方负责
     * 从相机帧的 Y 平面拷贝并去除行间填充；未识别到二维码返回 null。
     */
    fun decode(luminance: ByteArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || luminance.size < width * height) return null
        val source = PlanarYUVLuminanceSource(
            luminance,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return runCatching {
            MultiFormatReader().decode(bitmap, hints).text
        }.getOrNull()
    }
}
