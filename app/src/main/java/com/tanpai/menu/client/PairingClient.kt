package com.tanpai.menu.client

import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.net.HostAddress
import com.tanpai.menu.net.HostApiException
import com.tanpai.menu.net.HostRestClient
import java.io.IOException

/** 配对结果；[Failed.message] 为直接展示给用户的中文提示。 */
sealed interface PairOutcome {
    data object Success : PairOutcome

    data class Failed(val message: String) : PairOutcome
}

/**
 * 用一次性 6 位配对码换取 token 并落盘（`docs/design/0019` 第 2.2 节）。
 *
 * 只处理「用户输入 → 主机应答 → 保存凭据」这一段；连接与同步由 `ClientSession` 接管。
 */
class PairingClient(private val rest: HostRestClient, private val settings: SettingsStore) {

    suspend fun pair(addressInput: String, code: String, deviceName: String): PairOutcome {
        if (HostAddress.normalize(addressInput) == null) {
            return PairOutcome.Failed("主机地址格式应为 IP:端口，例如 192.168.1.5:8080")
        }
        if (code.length != CODE_LENGTH || !code.all { it.isDigit() }) {
            return PairOutcome.Failed("配对码是 6 位数字")
        }
        val name = deviceName.trim().ifBlank { settings.clientDeviceName }
        return try {
            val response = rest.pair(addressInput, code, settings.deviceId, name)
            settings.clientHostAddress = addressInput.trim()
            settings.clientToken = response.token
            settings.clientDeviceName = name
            PairOutcome.Success
        } catch (e: HostApiException) {
            PairOutcome.Failed(e.message ?: "配对失败，请重试")
        } catch (e: IOException) {
            PairOutcome.Failed("无法连接平板，请确认手机与平板在同一 Wi-Fi")
        }
    }

    private companion object {
        const val CODE_LENGTH = 6
    }
}