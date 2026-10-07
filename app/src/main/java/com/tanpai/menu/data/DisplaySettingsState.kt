package com.tanpai.menu.data

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tanpai.menu.net.protocol.DisplayPatchRequest
import com.tanpai.menu.net.protocol.DisplaySettingsDto
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 展示设置的可观察状态（`docs/design/0023` 第 2.2、2.4 节）。
 *
 * 展示页与管理页需要「有人改了设置就立刻重绘」，而 `SharedPreferences` 本身不可观察，因此由本类
 * 持有 [DisplaySettingsDto] 的 Compose 状态，并作为**唯一写入口**：
 * - 手机端 `PATCH /display` → 主机后端调用 [applyDisplay] → 展示页立即刷新；
 * - 平板自己的管理页改设置 → 同样走 [applyDisplay]，两条路径不会各改一份。
 *
 * 首次访问时从落盘值初始化（默认 [InMemoryDisplaySettings]，生产环境传 `SettingsStore`）。
 */
class DisplaySettingsState(private val store: DisplaySettings = InMemoryDisplaySettings()) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 当前展示设置；读取即建立 Compose 订阅。 */
    var value: DisplaySettingsDto by mutableStateOf(readFromStore())
        private set

    private fun readFromStore(): DisplaySettingsDto = DisplaySettingsDto(
        backgroundStyleId = store.backgroundStyleId,
        backgroundImagePath = store.backgroundImagePath,
        backgroundScrimPercent = store.backgroundScrimPercent,
        cardAlphaPercent = store.cardAlphaPercent,
        showStockHint = store.showStockHint
    )

    /**
     * 应用补丁并落盘：`null` = 不改；空串 = 清除。
     *
     * `backgroundStyleId` 与 `backgroundImagePath` 互斥——写其中一个就清掉另一个（`docs/design/0009`）。
     * 可在任意线程调用（HTTP 管线在 IO 线程），内部切回主线程发布状态。
     */
    fun applyDisplay(patch: DisplayPatchRequest) {
        val current = value
        val next = current.copy(
            backgroundScrimPercent = patch.backgroundScrimPercent ?: current.backgroundScrimPercent,
            cardAlphaPercent = patch.cardAlphaPercent ?: current.cardAlphaPercent,
            showStockHint = patch.showStockHint ?: current.showStockHint
        )
        // 背景只处理「本次补丁提到的那个」：提到样式就清图，提到图就清样式，都没提则不动。
        val nextBackground = when {
            patch.backgroundStyleId != null ->
                next.copy(
                    backgroundStyleId = patch.backgroundStyleId.takeIf { it.isNotBlank() },
                    backgroundImagePath = null
                )

            patch.backgroundImagePath != null ->
                next.copy(
                    backgroundImagePath = patch.backgroundImagePath.takeIf { it.isNotBlank() },
                    backgroundStyleId = null
                )

            else -> next
        }
        store.backgroundStyleId = nextBackground.backgroundStyleId
        store.backgroundImagePath = nextBackground.backgroundImagePath
        store.backgroundScrimPercent = nextBackground.backgroundScrimPercent
        store.cardAlphaPercent = nextBackground.cardAlphaPercent
        store.showStockHint = nextBackground.showStockHint
        // 必须等状态真正发布后再返回：HTTP 管线在 IO 线程，`post` 的更新可能还没执行，
        // 否则 `PATCH /display` 的响应体会是旧值（真机联调踩过）。
        commit(nextBackground)
    }

    /** 重新从落盘值读取并发布（导入备份等外部改动后调用）。 */
    fun reload() = commit(readFromStore())

    /**
     * 发布状态：主线程直接赋值；后台线程同步等待主线程完成（最多 [COMMIT_TIMEOUT_MILLIS] 毫秒，
     * 超时则自行赋值兜底，绝不阻塞业务）。
     */
    private fun commit(snapshot: DisplaySettingsDto) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            value = snapshot
            return
        }
        val latch = CountDownLatch(1)
        val posted = mainHandler.post {
            value = snapshot
            latch.countDown()
        }
        if (!posted) return
        runCatching { latch.await(COMMIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) }
    }

    private companion object {
        const val COMMIT_TIMEOUT_MILLIS = 1_000L
    }
}
