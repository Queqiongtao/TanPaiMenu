package com.tanpai.menu.net

import com.tanpai.menu.net.protocol.CategoryCreateRequest
import com.tanpai.menu.net.protocol.CategoryDto
import com.tanpai.menu.net.protocol.CategoryPatchRequest
import com.tanpai.menu.net.protocol.DeviceRole
import com.tanpai.menu.net.protocol.DishCreateRequest
import com.tanpai.menu.net.protocol.DishDto
import com.tanpai.menu.net.protocol.DishPatchRequest
import com.tanpai.menu.net.protocol.DisplayPatchRequest
import com.tanpai.menu.net.protocol.DisplaySettingsDto
import com.tanpai.menu.net.protocol.ErrorBody
import com.tanpai.menu.net.protocol.ImageUploadDto
import com.tanpai.menu.net.protocol.PairRequest
import com.tanpai.menu.net.protocol.PairResponse
import com.tanpai.menu.net.protocol.ProtocolJson
import com.tanpai.menu.net.protocol.SellRequest
import com.tanpai.menu.net.protocol.StateDto
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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
 * 主机 REST 客户端（`docs/design/0019` 第 2.4 节、`docs/design/0020` 第 2.2、2.4 节）。
 *
 * 只负责「发请求、解报文、抛契约错误」；重试、镜像、等待广播对齐都在 `client/ClientSession`。
 * 所有请求附带 `X-Device-Id` 与 `X-Device-Role: client`：主机据此识别手机并执行
 * 「允许手机编辑」判定（`docs/design/0020` 第 2.3 节）。
 *
 * [deviceId] 为本机标识提供者；配对前为空串时不发送 `X-Device-Id`。
 */
class HostRestClient(
    private val client: OkHttpClient,
    private val deviceId: () -> String = { "" }
) {

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
                .withClientIdentity()
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
                .withClientIdentity()
                .get()
                .header(AUTHORIZATION, "Bearer $token")
                .build()
        ) { ProtocolJson.decodeFromString(StateDto.serializer(), it) }

    /** `POST /categories`：新增分类。 */
    suspend fun createCategory(
        hostAddress: String,
        token: String,
        request: CategoryCreateRequest
    ): CategoryDto = execute(
        write(hostAddress, "categories", token)
            .post(jsonBody(CategoryCreateRequest.serializer(), request))
            .build()
    ) { ProtocolJson.decodeFromString(CategoryDto.serializer(), it) }

    /** `PATCH /categories/{id}`：改名 / 改排序。 */
    suspend fun patchCategory(
        hostAddress: String,
        token: String,
        id: Long,
        request: CategoryPatchRequest
    ): CategoryDto = execute(
        write(hostAddress, "categories/$id", token)
            .patch(jsonBody(CategoryPatchRequest.serializer(), request))
            .build()
    ) { ProtocolJson.decodeFromString(CategoryDto.serializer(), it) }

    /** `DELETE /categories/{id}`：删除分类（其下菜品级联下架）。 */
    suspend fun deleteCategory(hostAddress: String, token: String, id: Long) =
        executeEmpty(write(hostAddress, "categories/$id", token).delete().build())

    /** `POST /dishes`：新增菜品。 */
    suspend fun createDish(
        hostAddress: String,
        token: String,
        request: DishCreateRequest
    ): DishDto = execute(
        write(hostAddress, "dishes", token)
            .post(jsonBody(DishCreateRequest.serializer(), request))
            .build()
    ) { ProtocolJson.decodeFromString(DishDto.serializer(), it) }

    /** `PATCH /dishes/{id}`：局部更新；`imagePath` 传空串表示清空图片。 */
    suspend fun patchDish(
        hostAddress: String,
        token: String,
        id: Long,
        request: DishPatchRequest
    ): DishDto = execute(
        write(hostAddress, "dishes/$id", token)
            .patch(jsonBody(DishPatchRequest.serializer(), request))
            .build()
    ) { ProtocolJson.decodeFromString(DishDto.serializer(), it) }

    /** `DELETE /dishes/{id}`：删除菜品。 */
    suspend fun deleteDish(hostAddress: String, token: String, id: Long) =
        executeEmpty(write(hostAddress, "dishes/$id", token).delete().build())

    /** `POST /dishes/{id}/sell`：卖出一份（余量 -1，归零自动沽清）。 */
    suspend fun sell(hostAddress: String, token: String, id: Long, deviceId: String): DishDto =
        execute(
            write(hostAddress, "dishes/$id/sell", token)
                .post(jsonBody(SellRequest.serializer(), SellRequest(deviceId, UUID.randomUUID().toString())))
                .build()
        ) { ProtocolJson.decodeFromString(DishDto.serializer(), it) }

    /** `POST /dishes/{id}/unsell`：撤销该菜品最后一条卖出记录。 */
    suspend fun unsell(hostAddress: String, token: String, id: Long, deviceId: String): DishDto =
        execute(
            write(hostAddress, "dishes/$id/unsell", token)
                .post(jsonBody(SellRequest.serializer(), SellRequest(deviceId, UUID.randomUUID().toString())))
                .build()
        ) { ProtocolJson.decodeFromString(DishDto.serializer(), it) }

    /** `POST /menu/reset`：恢复默认菜单；主机随后广播快照，两端重新对齐。 */
    suspend fun resetMenu(hostAddress: String, token: String) =
        executeEmpty(write(hostAddress, "menu/reset", token).post(EMPTY_BODY).build())

    /**
     * `PATCH /display`：修改展示设置（`docs/design/0023` 第 2.2 节），返回应用后的完整设置。
     *
     * 字段为 null 表示不改；`backgroundImagePath` 为空串表示清除自定义背景图。
     */
    suspend fun updateDisplay(
        hostAddress: String,
        token: String,
        patch: DisplayPatchRequest
    ): DisplaySettingsDto = execute(
        write(hostAddress, "display", token)
            .patch(jsonBody(DisplayPatchRequest.serializer(), patch))
            .build()
    ) { ProtocolJson.decodeFromString(DisplaySettingsDto.serializer(), it) }

    /**
     * `POST /images`：multipart 上传压缩后的图片字节，返回主机上的相对路径。
     *
     * 主机按文件魔数判定类型，[contentType] 只作为分区声明；
     * [kind] 为 `background` 时主机按背景图规格落盘（长边 2048、JPEG 85，见 `docs/design/0023`）。
     */
    suspend fun uploadImage(
        hostAddress: String,
        token: String,
        bytes: ByteArray,
        contentType: String = JPEG_CONTENT_TYPE,
        kind: String? = null
    ): ImageUploadDto {
        val part = MultipartBody.Part.createFormData(
            IMAGE_FIELD,
            "menu.jpg",
            bytes.toRequestBody(contentType.toMediaType())
        )
        // 必须是 multipart/form-data：主机侧 NanoHTTPD 只在该类型下按分区解析 `file`，
        // 默认的 multipart/mixed 会被当成整块请求体，导致「缺少 file 字段」。
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(part)
            .build()
        val url = buildString {
            append(endpoint(hostAddress, "images"))
            if (kind != null) append("?kind=").append(kind)
        }
        val request = Request.Builder()
            .url(url)
            .withClientIdentity()
            .header(AUTHORIZATION, "Bearer $token")
            .post(body)
            .build()
        return execute(request) { ProtocolJson.decodeFromString(ImageUploadDto.serializer(), it) }
    }

    /** `POST /images?kind=background`：上传自定义背景图（规格由主机按 `kind` 决定）。 */
    suspend fun uploadBackgroundImage(
        hostAddress: String,
        token: String,
        bytes: ByteArray
    ): ImageUploadDto = uploadImage(hostAddress, token, bytes, kind = IMAGE_KIND_BACKGROUND)

    /** 主机地址原文 → `http://host:port/api/v1/<path>`；非法地址抛可展示的中文错误。 */
    private fun endpoint(hostAddress: String, path: String): String {
        val base = HostAddress.normalize(hostAddress)
            ?: throw HostApiException(0, null, "主机地址格式应为 IP:端口，例如 192.168.1.5:8080")
        return "$base/api/v1/$path"
    }

    /** 写请求的公共骨架：地址 + 身份头 + 鉴权头；调用方补方法、请求体与路径。 */
    private fun write(hostAddress: String, path: String, token: String): Request.Builder =
        Request.Builder()
            .url(endpoint(hostAddress, path))
            .withClientIdentity()
            .header(AUTHORIZATION, "Bearer $token")

    /** 统一的身份头；[deviceId] 为空时只发角色头。 */
    private fun Request.Builder.withClientIdentity(): Request.Builder = apply {
        deviceId().takeIf { it.isNotBlank() }?.let { header(DEVICE_ID_HEADER, it) }
        header(DEVICE_ROLE_HEADER, DeviceRole.CLIENT)
    }

    private fun <T> jsonBody(serializer: kotlinx.serialization.KSerializer<T>, value: T) =
        ProtocolJson.encodeToString(serializer, value).toRequestBody(JSON_MEDIA_TYPE)

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

    /** 204 等无响应体的写接口：只关心状态码。 */
    private suspend fun executeEmpty(request: Request) = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw toException(response.code, response.body?.string().orEmpty())
            }
        }
    }

    private fun toException(status: Int, text: String): HostApiException {
        val info = runCatching { ProtocolJson.decodeFromString(ErrorBody.serializer(), text).error }
            .getOrNull()
        return HostApiException(status, info?.code, info?.message ?: "平板返回 $status")
    }

    /** 对外可引用上传规格常量（客户端命令层需要 `?kind=background`）。 */
    companion object {
        const val AUTHORIZATION = "Authorization"
        const val DEVICE_ID_HEADER = "X-Device-Id"
        const val DEVICE_ROLE_HEADER = "X-Device-Role"
        const val IMAGE_FIELD = "file"
        const val JPEG_CONTENT_TYPE = "image/jpeg"

        /** `POST /images?kind=background`：按背景图规格落盘（`docs/design/0023`）。 */
        const val IMAGE_KIND_BACKGROUND = "background"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}
