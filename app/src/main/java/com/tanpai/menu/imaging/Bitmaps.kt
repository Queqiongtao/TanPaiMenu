package com.tanpai.menu.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream

/**
 * 图片解码与缩放（`docs/design/0020` 第 2.4 节）。
 *
 * 由 `ImageStore`（导入落盘）与 `ImageCompressor`（压缩成字节上传）共用，
 * 保证主机与手机两端产出的尺寸策略一致。
 */

/** 采样缩放解码：长边压到 [maxEdge] 以内；解码失败返回 null。调用方负责回收位图。 */
internal fun decodeScaled(input: InputStream, maxEdge: Int): Bitmap? {
    val bytes = input.readBytes()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxEdge * 2 ||
        bounds.outHeight / sampleSize > maxEdge * 2
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

    val longEdge = maxOf(decoded.width, decoded.height)
    if (longEdge <= maxEdge) return decoded

    val scale = maxEdge.toFloat() / longEdge
    val scaled = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * scale).toInt(),
        (decoded.height * scale).toInt(),
        true
    )
    if (scaled !== decoded) decoded.recycle()
    return scaled
}