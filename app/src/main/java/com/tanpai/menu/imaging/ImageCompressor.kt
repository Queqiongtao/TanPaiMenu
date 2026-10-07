package com.tanpai.menu.imaging

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片压缩（`docs/design/0020` 第 2.4 节）。
 *
 * 手机端把相册 / 拍照得到的 [Uri] 压成 JPEG 字节再上传；长边上限与压缩质量沿用
 * `ImageStore` 的默认值，两端产出的图片尺寸一致。
 */
object ImageCompressor {

    /** 菜品图长边上限，与主机 `ImageStore` 一致。 */
    const val MAX_EDGE = 1280

    /** JPEG 压缩质量，与主机 `ImageStore` 一致。 */
    const val JPEG_QUALITY = 80

    /** 读取 [uri] 并压成 JPEG 字节；解码失败返回 null。 */
    suspend fun compressToJpegBytes(
        context: Context,
        uri: Uri,
        maxEdge: Int = MAX_EDGE,
        jpegQuality: Int = JPEG_QUALITY
    ): ByteArray? = withContext(Dispatchers.IO) {
        val bitmap = context.contentResolver.openInputStream(uri)
            ?.use { decodeScaled(it, maxEdge) }
            ?: return@withContext null
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}