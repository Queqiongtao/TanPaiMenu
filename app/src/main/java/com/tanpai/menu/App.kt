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
import com.tanpai.menu.host.HostService
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
        logError = { message, throwable -> TpLog.e("host", message, throwable) }
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
            runCatching {
                if (container.settingsStore.hostEnabled) {
                    HostService.start(this@App)
                } else {
                    TpLog.i("host", "手机控制端已关闭，不启动主机服务")
                }
            }.onFailure { TpLog.e("host", "主机前台服务启动异常", it) }
        }
    }

    /** 幂等启动主机服务：已监听时直接返回；由 HostService 在 IO 线程调用。 */
    suspend fun ensureHostServerStarted() {
        if (container.hostServer?.isServing == true) return
        startHostServer()
    }

    /** 停止主机服务并释放实例；手机控制端被关闭或服务销毁时调用。 */
    fun stopHostServer() {
        container.hostServer?.stopServing()
        container.hostServer = null
    }

    /** 启动局域网主机服务；端口顺延后仍失败则记日志（平板本机功能不受影响）。 */
    private suspend fun startHostServer() {
        val preferred = container.settingsStore.serverPort
        val server = HostServer.start(
            api = container.hostApi,
            backend = container.menuBackend,
            repository = container.menuRepository,
            tokenProvider = { container.settingsStore.pairToken },
            scope = appScope,
            logInfo = { message -> TpLog.i("host", message) },
            logError = { message, throwable -> TpLog.e("host", message, throwable) },
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
            runCatching {
                val stale = container.database.deviceDao()
                    .deleteStale(System.currentTimeMillis() - DEVICE_RETENTION_MS)
                if (stale > 0) TpLog.i("host", "清理过期设备记录 $stale 条")
            }.onFailure { TpLog.e("host", "清理过期设备记录失败", it) }
            val address = HostInfo.address(HostInfo.lanIpv4(this), server.port)
                ?: "（未获取到局域网地址）"
            TpLog.i("host", "配对信息：$address 配对码 ${container.settingsStore.pairToken}")
        }
    }

    private companion object {
        /** 设备注册表保留时长：超过该时长未活跃的记录在服务启动时清理。 */
        const val DEVICE_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}