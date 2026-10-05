package com.tanpai.menu.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
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

    suspend fun importFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = context.contentResolver.openInputStream(uri)
            ?.use { decodeScaled(it) }
            ?: return@withContext null
        val target = File(imagesDir, "${UUID.randomUUID()}.jpg")
        target.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        bitmap.recycle()
        "$IMAGES_DIR/${target.name}"
    }

    fun delete(imagePath: String?) {
        if (imagePath.isNullOrBlank()) return
        File(context.filesDir, imagePath).delete()
    }

    suspend fun deleteOrphans(referenced: Set<String>) = withContext(Dispatchers.IO) {
        imagesDir.listFiles()?.forEach { file ->
            if ("$IMAGES_DIR/${file.name}" !in referenced) file.delete()
        }
        captureDir.listFiles()?.forEach { it.delete() }
    }

    private fun decodeScaled(input: InputStream): Bitmap? {
        val bytes = input.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_EDGE * 2 ||
            bounds.outHeight / sampleSize > MAX_EDGE * 2
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        val longEdge = maxOf(decoded.width, decoded.height)
        if (longEdge <= MAX_EDGE) return decoded

        val scale = MAX_EDGE.toFloat() / longEdge
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
        private const val JPEG_QUALITY = 80
    }
}
