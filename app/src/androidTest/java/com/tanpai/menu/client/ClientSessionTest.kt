package com.tanpai.menu.client

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.data.db.TpDatabase
import com.tanpai.menu.host.HostApi
import com.tanpai.menu.host.HostServer
import com.tanpai.menu.host.PairCodeManager
import com.tanpai.menu.host.RepositoryMenuBackend
import com.tanpai.menu.imaging.ImageStore
import com.tanpai.menu.net.HostApiException
import com.tanpai.menu.net.HostRestClient
import com.tanpai.menu.net.MenuSocketClient
import com.tanpai.menu.net.protocol.DishDto
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 客户端栈端到端验收（`docs/design/0019` 第 5 节）：真机 / 模拟器上连接本机 `HostServer`，
 * 跑通「配对 → 全量快照 → 改价收 dish.updated → 停服离线只读 → 重启自愈」。
 *
 * 走真实 OkHttp 与 NanoWSD，用于验证两端的 REST / WS 实现确实互通（含 WS 握手与关闭码）。
 */
@RunWith(AndroidJUnit4::class)
class ClientSessionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var db: TpDatabase
    private lateinit var repository: MenuRepository
    private lateinit var backend: RepositoryMenuBackend
    private lateinit var settings: SettingsStore
    private lateinit var pairCode: PairCodeManager
    private lateinit var server: HostServer
    private lateinit var session: ClientSession

    /** 「允许手机编辑」开关；测试里按需切换，同时驱动 `/state` 的同名字段。 */
    private var clientEditsAllowed = true

    private val restHttp = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** 心跳缩短到 5 秒，让断线检测在测试时限内完成（生产为 20 秒）。 */
    private val socketHttp = restHttp.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(5, TimeUnit.SECONDS)
        .build()

    private val rest = HostRestClient(restHttp, deviceId = { settings.deviceId })
    private val sockets = MenuSocketClient(socketHttp)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TpDatabase::class.java
        ).build()
        repository = MenuRepository(db, now = { 1000L })
        backend = RepositoryMenuBackend(
            repository = repository,
            imageStore = ImageStore(context),
            deviceDao = db.deviceDao(),
            storeName = { STORE_NAME },
            clientEditsEnabled = { clientEditsAllowed },
            now = { 2000L }
        )
        settings = SettingsStore(context)
        settings.clearClientPairing()
        pairCode = PairCodeManager(settings)
        startServer()
        runBlocking {
            val categoryId = repository.createCategory("热菜", 1)
            repository.createDish(
                Dish(
                    categoryId = categoryId,
                    name = "烤串",
                    priceCents = 300,
                    stockEnabled = true,
                    stockCount = 5,
                    updatedAt = 0L
                )
            )
        }
    }

    @After
    fun tearDown() {
        if (this::session.isInitialized) session.stop()
        if (this::server.isInitialized) server.stopServing()
        settings.clearClientPairing()
        scope.cancel()
        db.close()
    }

    @Test
    fun pairSnapshotEventAndRecover() = runBlocking {
        val deviceId = settings.deviceId
        val address = "127.0.0.1:${server.port}"

        val outcome = PairingClient(rest, settings).pair(address, pairCode.generate(), "测试手机")
        assertEquals(PairOutcome.Success, outcome)
        assertTrue("配对后应落盘主机地址", settings.clientHostAddress == address)
        assertEquals(settings.pairToken, settings.clientToken)

        session = ClientSession(settings, rest, sockets)
        session.start()

        val online = withTimeout(TIMEOUT_MS) {
            session.state.first { it.connection == ClientConnectionState.ONLINE }
        }
        assertEquals(1, online.dishes.size)
        assertEquals(300, online.dishes.first().priceCents)

        // 等主机把该设备登记进在线列表，确认 WS 订阅已建立，避免改价事件早于订阅而丢失。
        awaitTrue { server.onlineDevices().any { it.deviceId == deviceId } }

        val dish = repository.enabledDishes().first()
        repository.saveDish(dish.copy(priceCents = 1200))

        val updated = withTimeout(TIMEOUT_MS) {
            session.state.first { state -> state.dishes.any { it.priceCents == 1200 } }
        }
        assertEquals(1200, updated.dishes.first().priceCents)

        server.stopServing()
        val offline = withTimeout(TIMEOUT_MS) {
            session.state.first { it.connection == ClientConnectionState.RECONNECTING }
        }
        assertTrue("断线后应保留最后一次同步的菜单", offline.dishes.isNotEmpty())
        assertTrue("断线时应为只读态", offline.readOnly)

        startServer()
        val recovered = withTimeout(RECOVER_TIMEOUT_MS) {
            session.state.first { it.connection == ClientConnectionState.ONLINE }
        }
        assertEquals("重连后应重新拉到最新快照", 1200, recovered.dishes.first().priceCents)
    }

    @Test
    fun clientEditFlowKeepsBothSidesInSync() = runBlocking {
        pairAndStart()
        // 等主机把该设备登记进在线列表，确认 WS 订阅已建立，避免首次提交的广播早于订阅而空等。
        awaitTrue { server.onlineDevices().any { it.deviceId == settings.deviceId } }
        val dish = session.state.value.dishes.single()

        // 改价：镜像版本前进后价格更新
        assertTrue(session.submit(ClientCommand.UpdateDish(dish.id, dish.draft(priceCents = 1500))).isSuccess)
        assertEquals(1500, session.state.value.dishes.single().priceCents)

        // 沽清 / 恢复
        assertTrue(session.submit(ClientCommand.SetSoldOut(dish.id, soldOut = true)).isSuccess)
        assertTrue(session.state.value.dishes.single().soldOut)
        assertTrue(session.submit(ClientCommand.SetSoldOut(dish.id, soldOut = false)).isSuccess)
        assertTrue(!session.state.value.dishes.single().soldOut)

        // 卖出与撤销：余量回到 5
        assertTrue(session.submit(ClientCommand.Sell(dish.id)).isSuccess)
        assertEquals(4, session.state.value.dishes.single().stockCount)
        assertTrue(session.submit(ClientCommand.Unsell(dish.id)).isSuccess)
        assertEquals(5, session.state.value.dishes.single().stockCount)

        // 新增分类与菜品
        assertTrue(session.submit(ClientCommand.CreateCategory("饮品", 2)).isSuccess)
        val drinkCategoryId = session.state.value.categories.first { it.name == "饮品" }.id
        val drink = DishDraft(
            name = "酸梅汤",
            priceCents = 800,
            unit = "杯",
            imagePath = null,
            stockEnabled = false,
            stockCount = 0,
            highlight = null,
            description = "",
            sortOrder = 0
        )
        assertTrue(session.submit(ClientCommand.CreateDish(drinkCategoryId, drink)).isSuccess)
        assertTrue(session.state.value.dishes.any { it.name == "酸梅汤" })

        // 分类改名与删除（删除会级联下架其下菜品）
        assertTrue(session.submit(ClientCommand.UpdateCategory(drinkCategoryId, "冷饮", 2)).isSuccess)
        assertTrue(session.state.value.categories.any { it.name == "冷饮" })
        assertTrue(session.submit(ClientCommand.DeleteCategory(drinkCategoryId)).isSuccess)
        assertTrue(session.state.value.categories.none { it.id == drinkCategoryId })
        assertTrue(session.state.value.dishes.none { it.name == "酸梅汤" })

        // 上传图片：返回主机相对路径（图片不改变菜单版本，因此不与广播对齐）
        val imagePath = session.uploadImage(pngBytes()).getOrThrow()
        assertTrue(imagePath.endsWith(".jpg"))

        // 恢复默认菜单：两端一致回到 12 道菜，店名随快照下发
        assertTrue(session.submit(ClientCommand.ResetMenu).isSuccess)
        assertEquals(12, session.state.value.dishes.size)
        assertEquals(12, repository.enabledDishes().size)
        assertEquals(STORE_NAME, session.state.value.storeName)
    }

    @Test
    fun submitFailsWhenOffline() = runBlocking {
        session = ClientSession(settings, rest, sockets)

        val result = session.submit(ClientCommand.ResetMenu)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ClientSubmitException)
    }

    @Test
    fun submitIsRejectedWhenHostDisablesClientEdits() = runBlocking {
        clientEditsAllowed = false
        pairAndStart()

        assertTrue("开关关闭时不可编辑", !session.state.value.canEdit)
        val dish = session.state.value.dishes.single()
        val result = session.submit(ClientCommand.SetSoldOut(dish.id, soldOut = true))

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is HostApiException)
        assertEquals(403, (error as HostApiException).status)
    }

    private suspend fun pairAndStart() {
        val address = "127.0.0.1:${server.port}"
        val outcome = PairingClient(rest, settings).pair(address, pairCode.generate(), "测试手机")
        assertEquals(PairOutcome.Success, outcome)
        session = ClientSession(settings, rest, sockets)
        session.start()
        awaitTrue { session.state.value.connection == ClientConnectionState.ONLINE }
    }

    /** 合同测试里的菜品快照 → 编辑草稿；`soldOut` / `enabled` 不参与编辑。 */
    private fun DishDto.draft(priceCents: Int = this.priceCents) = DishDraft(
        name = name,
        priceCents = priceCents,
        unit = unit,
        imagePath = imagePath,
        stockEnabled = stockEnabled,
        stockCount = stockCount,
        highlight = highlight,
        description = description,
        sortOrder = sortOrder
    )

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF3366FF.toInt())
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun startServer() {
        server = requireNotNull(
            HostServer.start(
                api = HostApi(backend, { settings.pairToken }, pairing = pairCode),
                backend = backend,
                repository = repository,
                tokenProvider = { settings.pairToken },
                clientEditsAllowed = { clientEditsAllowed },
                scope = scope,
                logInfo = { },
                logError = { _, _ -> },
                preferredPort = TEST_PORT
            )
        ) { "测试主机未能绑定到 $TEST_PORT" }
    }

    private suspend fun awaitTrue(timeoutMs: Long = TIMEOUT_MS, condition: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) delay(50)
        }
    }

    private companion object {
        const val TEST_PORT = 18081
        const val TIMEOUT_MS = 10_000L
        const val RECOVER_TIMEOUT_MS = 45_000L
        const val STORE_NAME = "大金馄饨"
    }
}
