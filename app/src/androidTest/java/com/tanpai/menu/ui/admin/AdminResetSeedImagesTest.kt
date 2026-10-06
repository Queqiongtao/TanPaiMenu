package com.tanpai.menu.ui.admin

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tanpai.menu.backup.BackupManager
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.data.db.TpDatabase
import com.tanpai.menu.host.HostStatusProvider
import com.tanpai.menu.imaging.ImageStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「恢复默认菜单」验收：写回种子后 12 张默认图必须仍在磁盘上。
 * 该路径的孤儿清理若不包含菜品图，会把刚导入的默认图整批删掉。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AdminResetSeedImagesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: TpDatabase
    private lateinit var store: ImageStore
    private lateinit var repository: MenuRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, TpDatabase::class.java).build()
        store = ImageStore(context)
        repository = MenuRepository(db, { 1000L }, store::importSeedAsset)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun resetToDefaultMenuKeepsSeedImagesOnDisk() = runTest {
        repository.seedIfEmpty()
        val settings = SettingsStore(context)
        val viewModel = AdminViewModel(
            repository,
            settings,
            store,
            BackupManager(context, repository, store),
            HostStatusProvider(context, settings) { null }
        )

        viewModel.resetToDefaultMenu()
        viewModel.resetDone.first { it }

        val dishes = db.dishDao().getAll()
        assertEquals(12, dishes.size)
        dishes.forEach { dish ->
            val path = dish.imagePath
            assertTrue("${dish.name} 未写入默认图", path != null)
            assertTrue("${dish.name} 的默认图被孤儿清理误删", File(context.filesDir, path!!).isFile)
        }

        dishes.mapNotNull { it.imagePath }.forEach { store.delete(it) }
    }
}