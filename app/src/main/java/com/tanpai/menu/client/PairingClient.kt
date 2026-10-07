package com.tanpai.menu.client

import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.net.HostAddress
import com.tanpai.menu.net.HostApiException
import com.tanpai.menu.net.HostRestClient
import com.tanpai.menu.net.PairQr
import java.io.IOException

/** 配对结果；[Failed.message] 为直接展示给用户的中文提示。 */
sealed interface PairOutcome {
    data object Success : PairOutcome

    data class Failed(val message: String) : PairOutcome
}

/**
 * 用一次性 6 位配对码换取 token 并落盘（`docs/design/0019` 第 2.2 节），
 * 或直接落盘二维码里的地址与长期 token（`docs/design/0022` 第 2.1 节）。
 *
 * 只处理「用户输入 / 扫码结果 → 主机应答 → 保存凭据」这一段；连接与同步由 `ClientSession` 接管。
 */
class PairingClient(private val rest: HostRestClient, private val settings: SettingsStore) {

    suspend fun pair(addressInput: String, code: String, deviceName: String): PairOutcome {
        val requestedName = deviceName.trim()
        if (HostAddress.normalize(addressInput) == null) {
            return PairOutcome.Failed(ADDRESS_MESSAGE)
        }
        if (code.length != CODE_LENGTH || !code.all { it.isDigit() }) {
            return PairOutcome.Failed("配对码是 6 位数字")
        }
        return try {
            val response = rest.pair(addressInput, code, settings.deviceId, localName(requestedName))
            settings.clientHostAddress = addressInput.trim()
            settings.clientToken = response.token
            if (requestedName.isNotEmpty()) settings.clientDeviceName = requestedName
            PairOutcome.Success
        } catch (e: HostApiException) {
            PairOutcome.Failed(e.message ?: "配对失败，请重试")
        } catch (e: IOException) {
            PairOutcome.Failed(CONNECT_MESSAGE)
        }
    }

    /**
     * 扫码配对：解析二维码内容 → 直接把地址与长期令牌落盘，跳过 `POST /pair`。
     *
     * [deviceName] 为空表示沿用本机已保存的名称（扫码页不提供改名入口）；
     * 解析失败时返回 [PairQr.Result.Failed] 里的中文提示，界面继续扫描、不退出相机。
     */
    fun pairByQr(content: String, deviceName: String = ""): PairOutcome {
        val requestedName = deviceName.trim()
        val target = when (val result = PairQr.parse(content)) {
            is PairQr.Result.Failed -> return PairOutcome.Failed(result.reason)
            is PairQr.Result.Parsed -> result.target
        }
        settings.clientHostAddress = target.hostAddress
        settings.clientToken = target.token
        if (requestedName.isNotEmpty()) settings.clientDeviceName = requestedName
        return PairOutcome.Success
    }

    /** 设备名：调用方给的名字优先，否则沿用已保存的名称（不覆盖用户改过的名称）。 */
    private fun localName(requested: String): String =
        requested.ifEmpty { settings.clientDeviceName }

    private companion object {
        const val CODE_LENGTH = 6
        const val ADDRESS_MESSAGE = "主机地址格式应为 IP:端口，例如 192.168.1.5:8080"
        const val CONNECT_MESSAGE = "无法连接平板，请确认手机与平板在同一 Wi-Fi"
    }
}