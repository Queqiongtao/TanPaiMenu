package com.tanpai.menu.data

import android.content.Context
import android.os.Build
import java.util.UUID

class SettingsStore(context: Context) : PairCodeStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var storeName: String
        get() = prefs.getString(KEY_STORE_NAME, null) ?: ""
        set(value) = prefs.edit().putString(KEY_STORE_NAME, value).apply()

    /** 店名为空时使用的默认店名。 */
    val displayStoreName: String
        get() = storeName.ifBlank { DEFAULT_STORE_NAME }

    /** 展示页是否显示「有货 / 已售完 / 余 N」标识。 */
    var showStockHint: Boolean
        get() = prefs.getBoolean(KEY_SHOW_STOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_STOCK, value).apply()

    /** 展示页空闲时是否轻量自动滚动；任何触摸都会重置等待（`docs/design/0021`）。 */
    var autoScrollEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCROLL, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SCROLL, value).apply()

    /** 展示页菜品卡片底部不透明度（百分比，范围 [MIN_CARD_ALPHA_PERCENT]..100）。 */
    var cardAlphaPercent: Int
        get() = prefs.getInt(KEY_CARD_ALPHA, DEFAULT_CARD_ALPHA_PERCENT)
            .coerceIn(MIN_CARD_ALPHA_PERCENT, 100)
        set(value) = prefs
            .edit()
            .putInt(KEY_CARD_ALPHA, value.coerceIn(MIN_CARD_ALPHA_PERCENT, 100))
            .apply()

    /** 展示页背景图相对路径（images/xxx.jpg），null 表示未设置。 */
    var backgroundImagePath: String?
        get() = prefs.getString(KEY_BACKGROUND_IMAGE, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_BACKGROUND_IMAGE, value).apply()

    /**
     * 展示页内置背景样式 id，null 表示未选内置样式。
     *
     * 与 [backgroundImagePath] 互斥：同一时刻只有一种背景生效（由 ui/admin 在写入时保证）。
     */
    var backgroundStyleId: String?
        get() = prefs.getString(KEY_BACKGROUND_STYLE, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_BACKGROUND_STYLE, value).apply()

    /**
     * 展示页微信二维码图片相对路径（`images/<uuid>.png`），null 表示未设置。
     *
     * 由店主自行上传的二维码图，采用无损 PNG 保存；未设置时展示页不渲染该入口。
     */
    var wechatQrImagePath: String?
        get() = prefs.getString(KEY_WECHAT_QR_IMAGE, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_WECHAT_QR_IMAGE, value).apply()

    /** 展示页背景图上的白色蒙版不透明度（百分比，范围 0..80）。 */
    var backgroundScrimPercent: Int
        get() = prefs.getInt(KEY_BACKGROUND_SCRIM, DEFAULT_BACKGROUND_SCRIM_PERCENT)
            .coerceIn(MIN_BACKGROUND_SCRIM_PERCENT, MAX_BACKGROUND_SCRIM_PERCENT)
        set(value) = prefs
            .edit()
            .putInt(
                KEY_BACKGROUND_SCRIM,
                value.coerceIn(MIN_BACKGROUND_SCRIM_PERCENT, MAX_BACKGROUND_SCRIM_PERCENT)
            )
            .apply()

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

    /**
     * 主机配对码：客户端请求需带 `Authorization: Bearer <pairToken>`。
     * 首次读取时生成 32 字节随机十六进制并落盘，更换后旧码立即失效。
     */
    var pairToken: String
        get() {
            prefs.getString(KEY_PAIR_TOKEN, null)?.takeIf { it.isNotBlank() }?.let { return it }
            return generatePairToken()
        }
        set(value) = prefs.edit().putString(KEY_PAIR_TOKEN, value).apply()

    /** 主机服务端口偏好（1024–65535），实际绑定端口见 `HostServer.port`。 */
    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)
            .coerceIn(MIN_SERVER_PORT, MAX_SERVER_PORT)
        set(value) = prefs
            .edit()
            .putInt(KEY_SERVER_PORT, value.coerceIn(MIN_SERVER_PORT, MAX_SERVER_PORT))
            .apply()

    /** 是否启用手机控制端（主机前台服务）；关闭时不监听任何端口。 */
    var hostEnabled: Boolean
        get() = prefs.getBoolean(KEY_HOST_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HOST_ENABLED, value).apply()

    /**
     * 是否允许手机编辑菜单（`docs/design/0020` 第 2.3 节）。
     *
     * 关闭后主机拒绝带 `X-Device-Role: client` 的写请求；这是防误操作的软约束，不是安全边界。
     */
    var clientEditsEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLIENT_EDITS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CLIENT_EDITS_ENABLED, value).apply()

    /** 重新生成配对码并落盘，返回新码；调用方负责断开现有连接。 */
    fun regeneratePairToken(): String = generatePairToken().also { pairToken = it }

    private fun generatePairToken(): String {
        val bytes = ByteArray(PAIR_TOKEN_BYTES)
        java.security.SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString(separator = "") { "%02x".format(it) }
        prefs.edit().putString(KEY_PAIR_TOKEN, token).apply()
        return token
    }

    val deviceId: String
        get() {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            return generated
        }

    /**
     * 设备角色：null 表示尚未选择（首启显示角色选择页，见 `docs/design/0018`）。
     *
     * 刻意不设默认值：默认成主机的话，手机端会建库、导默认菜单并启动前台服务。
     */
    var role: Role?
        get() = Role.fromKey(prefs.getString(KEY_ROLE, null))
        set(value) = prefs.edit().putString(KEY_ROLE, value?.key).apply()

    /** 客户端上次配对的主机地址（`IP:端口` 原文）；未配对为 null。 */
    var clientHostAddress: String?
        get() = prefs.getString(KEY_CLIENT_HOST, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_CLIENT_HOST, value).apply()

    /** 客户端持有的主机 token；与 [clientHostAddress] 同时存在才会尝试连接。 */
    var clientToken: String?
        get() = prefs.getString(KEY_CLIENT_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_CLIENT_TOKEN, value).apply()

    /** 客户端设备名（默认取机型），注册到主机在线设备列表时使用。 */
    var clientDeviceName: String
        get() = prefs.getString(KEY_CLIENT_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }
            ?: Build.MODEL.orEmpty().ifBlank { DEFAULT_CLIENT_DEVICE_NAME }
        set(value) = prefs.edit().putString(KEY_CLIENT_DEVICE_NAME, value).apply()

    /** 当前一次性配对码；未点「显示配对码」时为 null。 */
    override var pairCode: String?
        get() = prefs.getString(KEY_PAIR_CODE, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_PAIR_CODE, value).apply()

    override var pairCodeExpiresAt: Long
        get() = prefs.getLong(KEY_PAIR_CODE_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_PAIR_CODE_EXPIRES_AT, value).apply()

    override var pairCodeFailures: Int
        get() = prefs.getInt(KEY_PAIR_CODE_FAILURES, 0)
        set(value) = prefs.edit().putInt(KEY_PAIR_CODE_FAILURES, value).apply()

    override var pairCodeLockUntil: Long
        get() = prefs.getLong(KEY_PAIR_CODE_LOCK_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_PAIR_CODE_LOCK_UNTIL, value).apply()

    override var pairedDeviceName: String?
        get() = prefs.getString(KEY_PAIRED_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_PAIRED_DEVICE_NAME, value).apply()

    /** 清除客户端配对信息（手机端「重新配对」时调用）。 */
    fun clearClientPairing() {
        prefs.edit().remove(KEY_CLIENT_HOST).remove(KEY_CLIENT_TOKEN).apply()
    }

    private companion object {
        /** 默认店名（首启未设置店名时展示与回填使用）。 */
        const val DEFAULT_STORE_NAME = "大金馄饨"
        const val PREFS_NAME = "tanpai_settings"
        const val KEY_STORE_NAME = "store_name"
        const val KEY_PIN_HASH = "admin_pin_hash"
        const val KEY_PIN_SALT = "admin_pin_salt"
        const val KEY_PIN_FAILURES = "admin_pin_failures"
        const val KEY_PIN_LOCK_UNTIL = "admin_pin_lock_until"
        const val KEY_AUTOSTART = "autostart_enabled"
        const val KEY_SHOW_STOCK = "show_stock"
        const val KEY_AUTO_SCROLL = "auto_scroll"
        const val KEY_CARD_ALPHA = "card_alpha"
        /** 卡片底不透明度默认值（百分比）。 */
        const val DEFAULT_CARD_ALPHA_PERCENT = 15
        /** 卡片底不透明度下限（百分比），避免卡片完全隐形。 */
        const val MIN_CARD_ALPHA_PERCENT = 15
        const val KEY_BACKGROUND_IMAGE = "background_image"
        const val KEY_BACKGROUND_STYLE = "background_style"
        const val KEY_WECHAT_QR_IMAGE = "wechat_qr_image"
        const val KEY_BACKGROUND_SCRIM = "background_scrim"
        /** 背景蒙版默认不透明度（百分比）。 */
        const val DEFAULT_BACKGROUND_SCRIM_PERCENT = 25
        /** 背景蒙版不透明度下限（百分比）。 */
        const val MIN_BACKGROUND_SCRIM_PERCENT = 0
        /** 背景蒙版不透明度上限（百分比），过高会完全遮住背景图。 */
        const val MAX_BACKGROUND_SCRIM_PERCENT = 80
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_PAIR_TOKEN = "pair_token"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_HOST_ENABLED = "host_enabled"
        const val KEY_CLIENT_EDITS_ENABLED = "client_edits_enabled"
        const val KEY_ROLE = "role"
        const val KEY_CLIENT_HOST = "client_host"
        const val KEY_CLIENT_TOKEN = "client_token"
        const val KEY_CLIENT_DEVICE_NAME = "client_device_name"
        /** 机型为空时的客户端设备名兜底。 */
        const val DEFAULT_CLIENT_DEVICE_NAME = "未命名手机"
        const val KEY_PAIR_CODE = "pair_code"
        const val KEY_PAIR_CODE_EXPIRES_AT = "pair_code_expires_at"
        const val KEY_PAIR_CODE_FAILURES = "pair_code_failures"
        const val KEY_PAIR_CODE_LOCK_UNTIL = "pair_code_lock_until"
        const val KEY_PAIRED_DEVICE_NAME = "paired_device_name"
        /** 配对码随机字节数（十六进制后 64 字符）。 */
        const val PAIR_TOKEN_BYTES = 32
        /** 主机服务默认端口。 */
        const val DEFAULT_SERVER_PORT = 8080
        const val MIN_SERVER_PORT = 1024
        const val MAX_SERVER_PORT = 65535
    }
}
