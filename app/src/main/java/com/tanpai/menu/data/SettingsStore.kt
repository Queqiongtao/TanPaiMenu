package com.tanpai.menu.data

import android.content.Context
import java.util.UUID

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var storeName: String
        get() = prefs.getString(KEY_STORE_NAME, null) ?: ""
        set(value) = prefs.edit().putString(KEY_STORE_NAME, value).apply()

    var adminPinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    var adminSalt: String?
        get() = prefs.getString(KEY_PIN_SALT, null)
        set(value) = prefs.edit().putString(KEY_PIN_SALT, value).apply()

    var pinFailures: Int
        get() = prefs.getInt(KEY_PIN_FAILURES, 0)
        set(value) = prefs.edit().putInt(KEY_PIN_FAILURES, value).apply()

    var pinLockUntil: Long
        get() = prefs.getLong(KEY_PIN_LOCK_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_PIN_LOCK_UNTIL, value).apply()

    var autostartEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOSTART, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOSTART, value).apply()

    val deviceId: String
        get() {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            return generated
        }

    private companion object {
        const val PREFS_NAME = "tanpai_settings"
        const val KEY_STORE_NAME = "store_name"
        const val KEY_PIN_HASH = "admin_pin_hash"
        const val KEY_PIN_SALT = "admin_pin_salt"
        const val KEY_PIN_FAILURES = "admin_pin_failures"
        const val KEY_PIN_LOCK_UNTIL = "admin_pin_lock_until"
        const val KEY_AUTOSTART = "autostart_enabled"
        const val KEY_DEVICE_ID = "device_id"
    }
}
