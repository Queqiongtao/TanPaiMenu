package com.tanpai.menu.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tanpai.menu.MainActivity
import com.tanpai.menu.data.SettingsStore

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SettingsStore(context).autostartEnabled) return
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(launch)
    }
}
