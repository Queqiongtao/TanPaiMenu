package com.tanpai.menu

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.tanpai.menu.backup.BackupManager
import com.tanpai.menu.client.ClientSession
import com.tanpai.menu.client.PairingClient
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.PinManager
import com.tanpai.menu.data.Role
import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.data.db.TpDatabase
import com.tanpai.menu.host.HostApi
import com.tanpai.menu.host.HostInfo
import com.tanpai.menu.host.HostServer
import com.tanpai.menu.host.HostService
import com.tanpai.menu.host.HostStatusProvider
import com.tanpai.menu.host.PairCodeManager
import com.tanpai.menu.host.RepositoryMenuBackend
import com.tanpai.menu.imaging.ImageStore
import com.tanpai.menu.logging.TpLog
import com.tanpai.menu.net.HostRestClient
import com.tanpai.menu.net.MenuSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val database: TpDatabase = TpDatabase.build(context)
    val imageStore: ImageStore = ImageStore(context)
    val menuRepository: MenuRepository = MenuRepository(database, seedImage = imageStore::importSeedAsset)
    val settingsStore: SettingsStore = SettingsStore(context)
    val pinManager: PinManager = PinManager(settingsStore)
    val backupManager: BackupManager = BackupManager(context, menuRepository, imageStore)

    /** 一次性 6 位配对码（`docs/design/0019` 第 2.1 节）。 */
    val pairCodeManager: PairCodeManager = PairCodeManager(settingsStore)

    /** 主机业务窄接口实现：HTTP 管线与 WS 广播共用。 */
    val menuBackend: RepositoryMenuBackend = RepositoryMenuBackend(
        repository = menuRepository,
        imageStore = imageStore,
        deviceDao = database.deviceDao()
    )

    val hostApi: HostApi = HostApi(
        backend = menuBackend,
        tokenProvider = { settingsStore.pairToken },
        logError = { message, throwable -> TpLog.e("host", message, throwable) },
        logInfo = { message -> TpLog.i("host", message) },
        pairing = pairCodeManager
    )

    /** 主机服务实例：启动成功后赋值，全为 null 表示未启动。 */
    var hostServer: HostServer? = null

    val hostStatus: HostStatusProvider = HostStatusProvider(
        context = context.applicationContext,
        settings = settingsStore,
        server = { hostServer },
        pairCode = pairCodeManager
    )

    /** REST 用 OkHttp：连接与读取超时都收紧到 5 秒，避免手机端长时间卡在「连接中」。 */
    private val restHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** WS 用独立 OkHttp：不设读超时，靠 20 秒 ping 保活并探测掉线（与服务端心跳呼应）。 */
    private val socketHttpClient: OkHttpClient = restHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(WS_PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build()

    val clientRest: HostRestClient = HostRestClient(restHttpClient)
    val clientSockets: MenuSocketClient = MenuSocketClient(socketHttpClient)

    /** 客户端连接会话；未配对时不产生任何网络请求。 */
    val clientSession: ClientSession = ClientSession(settingsStore, clientRest, clientSockets)

    val pairingClient: PairingClient = PairingClient(clientRest, settingsStore)

    private companion object {
        const val HTTP_TIMEOUT_SECONDS = 5L
        const val WS_PING_INTERVAL_SECONDS = 20L
    }
}

class App : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        TpLog.init(this)
        TpLog.i("app", "应用启动")
        container = AppContainer(this)
        when (container.settingsStore.role) {
            Role.HOST -> enterHostMode()
            Role.CLIENT -> {
                TpLog.i("app", "以手机客户端形态启动")
                container.clientSession.start()
            }

            null -> TpLog.i("app", "尚未选择角色，等待用户在角色选择页选择")
        }
    }

    /** 幂等进入主机形态：导默认菜单、清理孤儿图并拉起前台服务（`docs/design/0018` 第 2.2 节）。 */
    fun enterHostMode() {
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

    /** 切换角色：落盘并即时启停两套形态的后台任务（界面分流由 MainActivity 负责）。 */
    fun applyRole(role: Role) {
        if (container.settingsStore.role == role) return
        container.settingsStore.role = role
        TpLog.i("app", "切换角色为 ${role.key}")
        when (role) {
            Role.HOST -> {
                container.clientSession.stop()
                enterHostMode()
            }

            Role.CLIENT -> {
                HostService.stop(this)
                container.clientSession.start()
            }
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

    /**
     * 客户端形态下读取主机图片需要 `Authorization` 头；主机形态加载本地文件不经过 OkHttp，
     * 因此这里统一附加，未配对时（token 为空）不附加任何头。
     */
    override fun newImageLoader(): ImageLoader {
        val settings = SettingsStore(this)
        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val token = settings.clientToken
                        val request = chain.request()
                        if (token.isNullOrBlank()) {
                            chain.proceed(request)
                        } else {
                            chain.proceed(
                                request.newBuilder()
                                    .header("Authorization", "Bearer $token")
                                    .build()
                            )
                        }
                    }
                    .build()
            }
            .build()
    }

    private companion object {
        /** 设备注册表保留时长：超过该时长未活跃的记录在服务启动时清理。 */
        const val DEVICE_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}