package com.tanpai.menu.imaging

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanpai.menu.data.SeedMenu
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 内置默认菜品图验收：assets 齐全、导入落盘为 512 边长 JPEG、缺失 asset 降级为 null。
 */
@RunWith(AndroidJUnit4::class)
class ImageStoreSeedAssetTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ImageStore(context)

    @Test
    fun seedAssetsArePackaged(): Unit = runBlocking {
        SeedMenu.imageAssets.forEach { asset ->
            val bytes = context.assets
                .open("${ImageStore.SEED_ASSETS_DIR}/$asset")
                .use { it.readBytes() }
            assertTrue("$asset 未打包或为空", bytes.isNotEmpty())
        }
    }

    @Test
    fun importsEverySeedAssetScaledToMaxEdge(): Unit = runBlocking {
        SeedMenu.imageAssets.forEach { asset ->
            val path = store.importSeedAsset(asset)
            assertNotNull("$asset 导入失败", path)
            assertTrue(path!!.endsWith(".jpg"))

            val imported = File(context.filesDir, path)
            assertTrue(imported.isFile)
            val decoded = BitmapFactory.decodeFile(imported.absolutePath)
            assertNotNull(decoded)
            assertEquals(ImageStore.SEED_IMAGE_MAX_EDGE, maxOf(decoded.width, decoded.height))
            decoded.recycle()
            store.delete(path)
        }
    }

    @Test
    fun missingSeedAssetReturnsNull(): Unit = runBlocking {
        assertNull(store.importSeedAsset("not-exist.jpg"))
    }
}