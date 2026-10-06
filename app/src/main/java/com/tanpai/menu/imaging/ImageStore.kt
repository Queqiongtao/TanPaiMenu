package com.tanpai.menu.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID

class ImageStore(private val context: Context) {

    private val imagesDir: File
        get() = File(context.filesDir, IMAGES_DIR).apply { mkdirs() }

    private val captureDir: File
        get() = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }

    fun newCaptureUri(): Uri {
        val file = File(captureDir, "capture_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * 从 [uri] 导入图片并压缩保存，返回相对路径；解码失败返回 null。
     * [maxEdge] 为长边上限，[jpegQuality] 为 JPEG 压缩质量，默认沿用菜品缩略图参数。
     */
    suspend fun importFromUri(
        uri: Uri,
        maxEdge: Int = MAX_EDGE,
        jpegQuality: Int = JPEG_QUALITY
    ): String? = importAs(uri, maxEdge, "jpg", Bitmap.CompressFormat.JPEG, jpegQuality)

    /**
     * 从 [uri] 无损导入图片（PNG）并保存，用于二维码等不能容忍压缩失真的图。
     * [maxEdge] 为长边上限；解码失败返回 null。
     */
    suspend fun importLosslessFromUri(
        uri: Uri,
        maxEdge: Int = WECHAT_QR_MAX_EDGE
    ): String? = importAs(uri, maxEdge, "png", Bitmap.CompressFormat.PNG, 100)

    /**
     * 从内存字节导入图片（`POST /images` 上传路径），复用「采样缩放 → 长边 ≤1280 → JPEG 质量 80」，
     * 返回相对路径；解码失败返回 null。
     */
    suspend fun importFromBytes(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(ByteArrayInputStream(bytes), MAX_EDGE) ?: return@withContext null
        saveBitmap(bitmap, "jpg", Bitmap.CompressFormat.JPEG, JPEG_QUALITY)
    }

    /**
     * 导入内置默认菜品图：从 `assets/[SEED_ASSETS_DIR]` 读取并压缩保存，返回相对路径。
     * asset 缺失或解码失败返回 null，调用方按「无图」处理。
     */
    suspend fun importSeedAsset(assetName: String): String? = withContext(Dispatchers.IO) {
        val bitmap = runCatching {
            context.assets.open("$SEED_ASSETS_DIR/$assetName").use { decodeScaled(it, SEED_IMAGE_MAX_EDGE) }
        }.getOrNull() ?: return@withContext null
        saveBitmap(bitmap, "jpg", Bitmap.CompressFormat.JPEG, JPEG_QUALITY)
    }

    /** 解码 + 缩放 + 落盘的共用实现，[extension] 与 [format] 决定输出格式。 */
    private suspend fun importAs(
        uri: Uri,
        maxEdge: Int,
        extension: String,
        format: Bitmap.CompressFormat,
        quality: Int
    ): String? = withContext(Dispatchers.IO) {
        val bitmap = context.contentResolver.openInputStream(uri)
            ?.use { decodeScaled(it, maxEdge) }
            ?: return@withContext null
        saveBitmap(bitmap, extension, format, quality)
    }

    /** 把 [bitmap] 压缩落盘到 `images/<uuid>.<extension>` 并回收，返回相对路径。 */
    private fun saveBitmap(
        bitmap: Bitmap,
        extension: String,
        format: Bitmap.CompressFormat,
        quality: Int
    ): String {
        val target = File(imagesDir, "${UUID.randomUUID()}.$extension")
        target.outputStream().use { out ->
            bitmap.compress(format, quality, out)
        }
        bitmap.recycle()
        return "$IMAGES_DIR/${target.name}"
    }

    fun delete(imagePath: String?) {
        if (imagePath.isNullOrBlank()) return
        File(context.filesDir, imagePath).delete()
    }

    fun readBytes(relativePath: String): ByteArray? =
        File(context.filesDir, relativePath).takeIf { it.isFile }?.readBytes()

    fun writeImage(name: String, bytes: ByteArray): String? {
        if (!IMAGE_NAME.matches(name)) return null
        val target = File(imagesDir, name.lowercase(Locale.US))
        target.outputStream().use { it.write(bytes) }
        return "$IMAGES_DIR/${target.name}"
    }

    suspend fun deleteOrphans(referenced: Set<String>): Int = withContext(Dispatchers.IO) {
        var removed = 0
        imagesDir.listFiles()?.forEach { file ->
            if ("$IMAGES_DIR/${file.name}" !in referenced && file.delete()) removed++
        }
        captureDir.listFiles()?.forEach { it.delete() }
        removed
    }

    private fun decodeScaled(input: InputStream, maxEdge: Int): Bitmap? {
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

    companion object {
        const val IMAGES_DIR = "images"
        private const val CAPTURE_DIR = "capture"
        private const val MAX_EDGE = 1280
        /** 内置默认菜品图所在 assets 子目录。 */
        const val SEED_ASSETS_DIR = "dishes"
        /** 内置默认菜品图长边：源图 2048，展示端缩略图 56dp（T10 上约 149px），512 有约 3.4 倍余量。 */
        const val SEED_IMAGE_MAX_EDGE = 512
        private const val JPEG_QUALITY = 80
        /** 背景图长边上限，明显高于缩略图以适配平板整屏铺满。 */
        const val BACKGROUND_MAX_EDGE = 2048
        /** 背景图 JPEG 压缩质量。 */
        const val BACKGROUND_JPEG_QUALITY = 85
        /** 微信二维码长边上限：略大于放大浮层的显示边长，且无损输出。 */
        const val WECHAT_QR_MAX_EDGE = 1024
        private val IMAGE_NAME =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|png)")
    }
}
