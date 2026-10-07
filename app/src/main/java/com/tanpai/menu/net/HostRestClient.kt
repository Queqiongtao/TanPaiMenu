package com.tanpai.menu.net

import com.tanpai.menu.net.protocol.ErrorBody
import com.tanpai.menu.net.protocol.PairRequest
import com.tanpai.menu.net.protocol.PairResponse
import com.tanpai.menu.net.protocol.ProtocolJson
import com.tanpai.menu.net.protocol.StateDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 主机返回的契约错误（`docs/design/0001` 第 3.1 节）；[code] 为契约错误码。 */
class HostApiException(
    val status: Int,
    val code: String?,
    message: String
) : Exception(message) {

    /** 配对失效（401）：客户端据此停止重试并要求重新配对。 */
    val isUnauthorized: Boolean get() = status == HTTP_UNAUTHORIZED

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}

/**
 * 主机 REST 客户端（`docs/design/0019` 第 2.4 节）。
 *
 * 只负责「发请求、解报文、抛契约错误」；重试、镜像与状态机在 `client/ClientSession`。
 * 写入类接口留到 M11，本类当前只有配对与快照两个读路径。
 */
class HostRestClient(private val client: OkHttpClient) {

    /**
     * `POST /pair`：用一次性配对码换取长期 token。
     *
     * [hostAddress] 为主机地址原文（`IP:端口` 或 `http://IP:端口`），由 [endpoint] 统一规范化，
     * 调用方不需要预先归一化。
     */
    suspend fun pair(hostAddress: String, code: String, deviceId: String, name: String): PairResponse =
        execute(
            Request.Builder()
                .url(endpoint(hostAddress, "pair"))
                .post(
                    ProtocolJson
                        .encodeToString(PairRequest.serializer(), PairRequest(code, deviceId, name))
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build()
        ) { ProtocolJson.decodeFromString(PairResponse.serializer(), it) }

    /** `GET /state`：全量快照，客户端连接与重连首先调用；[hostAddress] 同样为主机地址原文。 */
    suspend fun state(hostAddress: String, token: String): StateDto =
        execute(
            Request.Builder()
                .url(endpoint(hostAddress, "state"))
                .get()
                .header(AUTHORIZATION, "Bearer $token")
                .build()
        ) { ProtocolJson.decodeFromString(StateDto.serializer(), it) }

    /** 主机地址原文 → `http://host:port/api/v1/<path>`；非法地址抛可展示的中文错误。 */
    private fun endpoint(hostAddress: String, path: String): String {
        val base = HostAddress.normalize(hostAddress)
            ?: throw HostApiException(0, null, "主机地址格式应为 IP:端口，例如 192.168.1.5:8080")
        return "$base/api/v1/$path"
    }

    private suspend fun <T> execute(request: Request, decode: (String) -> T): T =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw toException(response.code, text)
                runCatching { decode(text) }.getOrElse {
                    throw HostApiException(response.code, null, "平板返回的数据无法解析")
                }
            }
        }

    private fun toException(status: Int, text: String): HostApiException {
        val info = runCatching { ProtocolJson.decodeFromString(ErrorBody.serializer(), text).error }
            .getOrNull()
        return HostApiException(status, info?.code, info?.message ?: "平板返回 $status")
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}