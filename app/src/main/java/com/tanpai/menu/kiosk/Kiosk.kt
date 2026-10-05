package com.tanpai.menu.kiosk

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun KeepScreenOn() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

fun exitToHome(context: Context) {
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val packageManager = context.packageManager
    val candidates = packageManager.queryIntentActivities(homeIntent, 0)
        .map { it.activityInfo }
        .filter { it.packageName != context.packageName && !it.name.endsWith(FALLBACK_HOME) }
        .map { ComponentName(it.packageName, it.name) }
    val defaultHome = packageManager
        .resolveActivity(homeIntent, 0)
        ?.activityInfo
        ?.let { ComponentName(it.packageName, it.name) }
    val target = candidates.firstOrNull { it == defaultHome } ?: candidates.firstOrNull()
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        if (target != null) component = target
    }
    runCatching { context.startActivity(intent) }
}

private const val FALLBACK_HOME = "FallbackHome"

fun openHomeSettings(context: Context) {
    val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(intent) }
}
