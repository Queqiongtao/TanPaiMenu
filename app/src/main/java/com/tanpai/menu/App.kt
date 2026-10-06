package com.tanpai.menu

import android.app.Application
import android.content.Context
import com.tanpai.menu.backup.BackupManager
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.PinManager
import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.data.db.TpDatabase
import com.tanpai.menu.host.HostApi
import com.tanpai.menu.host.HostInfo
import com.tanpai.menu.host.HostServer
import com.tanpai.menu.host.HostStatusProvider
import com.tanpai.menu.host.RepositoryMenuBackend
import com.tanpai.menu.imaging.ImageStore
import com.tanpai.menu.logging.TpLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val database: TpDatabase = TpDatabase.build(context)
    val imageStore: ImageStore = ImageStore(context)
    val menuRepository: MenuRepository = MenuRepository(database, seedImage = imageStore::importSeedAsset)
    val settingsStore: SettingsStore = SettingsStore(context)
    val pinManager: PinManager = PinManager(settingsStore)
    val backupManager: BackupManager = BackupManager(context, menuRepository, imageStore)

    /** 主机业务窄接口实现：HTTP 管线与 WS 广播共用。 */
    val menuBackend: RepositoryMenuBackend = RepositoryMenuBackend(
        repository = menuRepository,
        imageStore = imageStore,
        deviceDao = database.deviceDao()
    )

    val hostApi: HostApi = HostApi(
        backend = menuBackend,
        tokenProvider = { settingsStore.pairToken },
        log = { message, throwable -> TpLog.e("host", message, throwable) }
    )

    /** 主机服务实例：`App.onCreate` 启动成功后赋值，全为 null 表示未启动。 */
    var hostServer: HostServer? = null

    val hostStatus: HostStatusProvider =
        HostStatusProvider(context.applicationContext, settingsStore) { hostServer }
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
            runCatching { startHostServer() }
                .onFailure { TpLog.e("host", "主机服务启动异常", it) }
        }
    }

    /** 启动局域网主机服务；端口顺延后仍失败则记日志（平板本机功能不受影响）。 */
    private fun startHostServer() {
        val preferred = container.settingsStore.serverPort
        val server = HostServer.start(
            api = container.hostApi,
            backend = container.menuBackend,
            repository = container.menuRepository,
            tokenProvider = { container.settingsStore.pairToken },
            scope = appScope,
            log = { message, throwable -> TpLog.e("host", message, throwable) },
            preferredPort = preferred
        )
        container.hostServer = server
        if (server == null) {
            TpLog.e(
                "host",
                "主机服务启动失败：端口 $preferred 起连续 ${HostServer.PORT_FALLBACK_COUNT} 个端口均被占用",
                null
            )
        } else {
            val address = HostInfo.address(HostInfo.lanIpv4(this), server.port)
                ?: "（未获取到局域网地址）"
            TpLog.i("host", "配对信息：$address 配对码 ${container.settingsStore.pairToken}")
        }
    }
}