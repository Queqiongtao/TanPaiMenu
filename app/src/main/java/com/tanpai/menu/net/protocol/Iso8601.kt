package com.tanpai.menu.net.protocol

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 契约时间格式：ISO-8601 UTC、毫秒精度，例如 2026-10-05T03:20:30.123Z。
 *
 * 只用 SimpleDateFormat / Date（API 24 可用），不依赖 java.time 与脱糖。
 * SimpleDateFormat 非线程安全，按线程缓存实例。
 */
object Iso8601 {

    private const val PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    private val formatters = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat(PATTERN, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
    }

    fun format(epochMillis: Long): String =
        requireNotNull(formatters.get()).format(Date(epochMillis))

    /** 解析失败返回 null，由调用方按 invalid_argument 处理。 */
    fun parseOrNull(text: String): Long? = try {
        requireNotNull(formatters.get()).parse(text)?.time
    } catch (e: ParseException) {
        null
    }
}