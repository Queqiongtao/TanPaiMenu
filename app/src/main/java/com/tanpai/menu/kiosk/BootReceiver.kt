package com.tanpai.menu.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tanpai.menu.MainActivity
import com.tanpai.menu.data.Role
import com.tanpai.menu.data.SettingsStore

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = SettingsStore(context)
        if (!settings.autostartEnabled) return
        // 只有平板主机形态才随开机自启（docs/design/0018 第 2.2 节）。
        if (settings.role != Role.HOST) return
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(launch)
    }
}