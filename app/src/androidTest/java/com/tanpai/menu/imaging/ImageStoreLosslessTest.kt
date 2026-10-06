package com.tanpai.menu.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 二维码无损导入验收：按长边缩放、输出 PNG、落盘可解码。
 */
@RunWith(AndroidJUnit4::class)
class ImageStoreLosslessTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ImageStore(context)

    @Test
    fun importsLosslessPngAndScalesToMaxEdge(): Unit = runBlocking {
        val source = File(context.filesDir, "qr_lossless_test_source.png")
        val side = 2000
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val path = store.importLosslessFromUri(Uri.fromFile(source))
        assertNotNull(path)
        assertTrue(path!!.endsWith(".png"))

        val imported = File(context.filesDir, path)
        assertTrue(imported.isFile)
        val decoded = BitmapFactory.decodeFile(imported.absolutePath)
        assertNotNull(decoded)
        assertEquals(ImageStore.WECHAT_QR_MAX_EDGE, maxOf(decoded.width, decoded.height))
        decoded.recycle()

        store.delete(path)
        source.delete()
    }
}
