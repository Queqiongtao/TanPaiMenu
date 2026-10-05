package com.tanpai.menu.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 现场排障用的轻量日志：同时写 logcat 与 `filesDir/logs/app-<date>.log`，本地保留最近 7 天。
 * 普通日志异步落盘，崩溃日志同步落盘以免进程退出后丢失。
 */
object TpLog {

    const val TAG = "TanPaiMenu"
    const val LOG_DIR = "logs"
    const val RETENTION_DAYS = 7L

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tp-log").apply { isDaemon = true }
    }
    private val lock = Any()

    @Volatile
    private var logDir: File? = null

    fun init(context: Context) {
        val dir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        logDir = dir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("crash", "未捕获异常，线程=${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
        i("app", "日志已启用，目录=${dir.absolutePath}")
        prune(dir, System.currentTimeMillis())
    }

    fun d(tag: String, message: String) = log('D', tag, message, null, false)

    fun i(tag: String, message: String) = log('I', tag, message, null, false)

    fun w(tag: String, message: String) = log('W', tag, message, null, false)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log('E', tag, message, throwable, true)

    fun currentLogFile(now: Long = System.currentTimeMillis()): File? =
        logDir?.let { File(it, fileName(now)) }

    private fun log(
        level: Char,
        tag: String,
        message: String,
        throwable: Throwable?,
        sync: Boolean
    ) {
        val line = formatLine(level, tag, message, throwable)
        when (level) {
            'D' -> Log.d("$TAG/$tag", message, throwable)
            'I' -> Log.i("$TAG/$tag", message, throwable)
            'W' -> Log.w("$TAG/$tag", message, throwable)
            else -> Log.e("$TAG/$tag", message, throwable)
        }
        val dir = logDir ?: return
        if (sync) {
            append(dir, line)
        } else {
            executor.execute { append(dir, line) }
        }
    }

    private fun formatLine(level: Char, tag: String, message: String, throwable: Throwable?): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val builder = StringBuilder()
            .append(stamp).append(' ')
            .append(level).append('/').append(tag).append(' ')
            .append(message)
        throwable?.let { builder.append('\n').append(Log.getStackTraceString(it)) }
        return builder.toString()
    }

    private fun append(dir: File, line: String) {
        synchronized(lock) {
            runCatching {
                val file = File(dir, fileName(System.currentTimeMillis()))
                file.appendText(line + "\n", Charsets.UTF_8)
            }
        }
    }

    private fun prune(dir: File, now: Long) {
        val deadline = now - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < deadline) file.delete()
        }
    }

    private fun fileName(now: Long): String =
        "app-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)) + ".log"
}
