package com.tanpai.menu

import android.app.Application
import android.content.Context
import com.tanpai.menu.backup.BackupManager
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.PinManager
import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.data.db.TpDatabase
import com.tanpai.menu.imaging.ImageStore
import com.tanpai.menu.logging.TpLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val database: TpDatabase = TpDatabase.build(context)
    val menuRepository: MenuRepository = MenuRepository(database)
    val settingsStore: SettingsStore = SettingsStore(context)
    val pinManager: PinManager = PinManager(settingsStore)
    val imageStore: ImageStore = ImageStore(context)
    val backupManager: BackupManager = BackupManager(context, menuRepository, imageStore)
}

class App : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        TpLog.init(this)
        TpLog.i("app", "应用启动")
        container = AppContainer(this)
        appScope.launch {
            runCatching {
                container.menuRepository.seedIfEmpty()
                val referenced = container.menuRepository.allImagePaths() +
                    setOfNotNull(
                        container.settingsStore.backgroundImagePath,
                        container.settingsStore.wechatQrImagePath
                    )
                val removed = container.imageStore.deleteOrphans(referenced)
                if (removed > 0) TpLog.i("image", "启动清理孤儿图片 $removed 张")
            }.onFailure { TpLog.e("app", "启动初始化失败", it) }
        }
    }
}
