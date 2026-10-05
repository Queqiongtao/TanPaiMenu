package com.tanpai.menu.data

import java.security.MessageDigest
import java.security.SecureRandom

class PinManager(private val settings: SettingsStore) {

    fun isPinSet(): Boolean = settings.adminPinHash != null && settings.adminSalt != null

    fun setPin(pin: String) {
        val salt = randomHex(SALT_BYTES)
        settings.adminSalt = salt
        settings.adminPinHash = hash(pin, salt)
        settings.pinFailures = 0
        settings.pinLockUntil = 0L
    }

    fun isLocked(now: Long = System.currentTimeMillis()): Boolean = now < settings.pinLockUntil

    fun lockRemainingSeconds(now: Long = System.currentTimeMillis()): Int =
        ((settings.pinLockUntil - now + 999L) / 1000L).toInt().coerceAtLeast(0)

    fun verify(pin: String, now: Long = System.currentTimeMillis()): Boolean {
        if (isLocked(now)) return false
        val salt = settings.adminSalt ?: return false
        val expected = settings.adminPinHash ?: return false
        if (hash(pin, salt) == expected) {
            settings.pinFailures = 0
            settings.pinLockUntil = 0L
            return true
        }
        val failures = settings.pinFailures + 1
        if (failures >= MAX_FAILURES) {
            settings.pinFailures = 0
            settings.pinLockUntil = now + LOCK_DURATION_MS
        } else {
            settings.pinFailures = failures
        }
        return false
    }

    private fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(Charsets.UTF_8))
        return digest.digest(pin.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun randomHex(size: Int): String {
        val buffer = ByteArray(size)
        SecureRandom().nextBytes(buffer)
        return buffer.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

    companion object {
        const val MAX_FAILURES = 5
        const val LOCK_DURATION_MS = 60_000L
        private const val SALT_BYTES = 16
    }
}
