package com.tanpai.menu.host

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tanpai.menu.App
import com.tanpai.menu.MainActivity
import com.tanpai.menu.R
import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.logging.TpLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 手机控制端的前台服务（`docs/design/0016` 第 2 章）。
 *
 * 只承担「保活 + 常驻通知」：先把通知挂到前台，再在 IO 线程上幂等地拉起 `HostServer`；
 * 服务端实例仍由 `AppContainer` 持有，UI 通过 `HostStatusProvider` 读取。
 */
class HostService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        scope.launch { (application as App).ensureHostServerStarted() }
        startRefreshLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        refreshJob = null
        val app = application as App
        runBlocking(Dispatchers.IO) { app.stopHostServer() }
        scope.cancel()
        TpLog.i(TAG, "主机前台服务已停止")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 通知渠道（API 26+ 必需）；低重要级别，避免弹横幅与响铃。 */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.host_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.host_notification_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        TpLog.i(TAG, "主机前台服务已启动")
    }

    /** 每 30 秒刷新一次通知文本，覆盖 Wi-Fi 地址变化。 */
    private fun startRefreshLoop() {
        if (refreshJob != null) return
        refreshJob = scope.launch {
            while (true) {
                delay(NOTIFICATION_REFRESH_MS)
                val manager = getSystemService(NotificationManager::class.java) ?: continue
                manager.notify(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    /** 通知内容：店名 + 运行状态 + 局域网地址（`docs/design/0016` 第 2.3 节）。 */
    private fun buildNotification(): Notification {
        val storeName = SettingsStore(this).displayStoreName
        val status = (application as App).container.hostStatus
        val text = when {
            !status.running -> getString(R.string.host_notification_stopped)
            else -> status.address()
                ?.let { getString(R.string.host_notification_running, it) }
                ?: getString(R.string.host_notification_no_wifi)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_host_service)
            .setContentTitle(storeName)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "host"
        private const val CHANNEL_ID = "host_service"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_REFRESH_MS = 30_000L

        /** 启动前台服务；调用方已确认 `SettingsStore.hostEnabled` 为真。 */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, intent(context)) }
                .onFailure { TpLog.e(TAG, "启动主机前台服务失败", it) }
        }

        /** 停止前台服务：`onDestroy` 会停监听、断开全部连接并移除通知。 */
        fun stop(context: Context) {
            runCatching { context.stopService(intent(context)) }
                .onFailure { TpLog.e(TAG, "停止主机前台服务失败", it) }
        }

        private fun intent(context: Context) = Intent(context, HostService::class.java)
    }
}