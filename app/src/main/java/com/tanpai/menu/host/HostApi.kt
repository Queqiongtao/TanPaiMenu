package com.tanpai.menu.host

import com.tanpai.menu.data.DisplaySpec
import com.tanpai.menu.data.db.DishHighlight
import com.tanpai.menu.net.protocol.CategoryCreateRequest
import com.tanpai.menu.net.protocol.CategoryPatchRequest
import com.tanpai.menu.net.protocol.DishCreateRequest
import com.tanpai.menu.net.protocol.DishPatchRequest
import com.tanpai.menu.net.protocol.DisplayPatchRequest
import com.tanpai.menu.net.protocol.ErrorCode
import com.tanpai.menu.net.protocol.ImageUploadDto
import com.tanpai.menu.net.protocol.PairRequest
import com.tanpai.menu.net.protocol.PairResponse
import com.tanpai.menu.net.protocol.ProtocolJson
import com.tanpai.menu.net.protocol.SellRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * 主机请求管线（纯 Kotlin，可在 JVM 单测直接验证，见 `docs/design/0015` 第 2.2 节）。
 *
 * 处理顺序：鉴权 → requestId 幂等查表 → 参数校验 → 单写者执行 → 返回响应。
 * 业务失败不抛出，统一映射为契约错误码（400 / 401 / 404 / 409 / 413 / 500）。
 */
class HostApi(
    private val backend: MenuBackend,
    private val tokenProvider: () -> String,
    private val logError: (String, Throwable?) -> Unit = { _, _ -> },
    private val logInfo: (String) -> Unit = {},
    private val pairing: PairCodeVerifier = PairCodeVerifier { _, _ -> PairVerdict.Invalid },
    private val cacheCapacity: Int = IDEMPOTENCY_CAPACITY,
    private val maxImageBytes: Int = MAX_IMAGE_BYTES
) {

    private val cacheLock = Any()
    private val idempotency = LinkedHashMap<String, HostResponse>()

    suspend fun handle(request: HostRequest): HostResponse {
        if (request.bearerToken != tokenProvider()) return unauthorized()
        return try {
            route(request)
        } catch (e: Exception) {
            internalError(request.path, e)
        }
    }

    /**
     * `POST /images`：multipart 由传输层解析后交给管线做类型与大小校验。
     *
     * [kind] 来自查询参数（`docs/design/0023` 第 2.3 节）：`background` 表示按背景图规格落盘
     * （长边 2048、JPEG 85），其余值沿用菜品图规格（长边 1280、JPEG 80）。
     */
    suspend fun handleImageUpload(
        bearerToken: String?,
        contentType: String?,
        bytes: ByteArray,
        kind: String? = null
    ): HostResponse {
        if (bearerToken != tokenProvider()) return unauthorized()
        val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (mediaType != "image/jpeg" && mediaType != "image/png") {
            return failure(400, ErrorCode.INVALID_ARGUMENT, "仅支持 image/jpeg 与 image/png")
        }
        if (bytes.isEmpty()) {
            return failure(400, ErrorCode.INVALID_ARGUMENT, "图片内容为空")
        }
        if (bytes.size > maxImageBytes) {
            return failure(413, ErrorCode.PAYLOAD_TOO_LARGE, "图片超过 10MB")
        }
        val background = kind.equals(IMAGE_KIND_BACKGROUND, ignoreCase = true)
        return try {
            val path = if (background) {
                backend.saveBackgroundImage(bytes)
            } else {
                backend.saveImage(bytes)
            } ?: return failure(400, ErrorCode.INVALID_ARGUMENT, "图片解码失败")
            json(
                201,
                encodeJson(
                    ImageUploadDto(
                        imagePath = path,
                        url = "/api/v1/$path"
                    )
                )
            )
        } catch (e: Exception) {
            internalError("POST /images", e)
        }
    }

    /**
     * `POST /pair`：用一次性 6 位配对码换取长期 token（`docs/design/0019` 第 3 节）。
     *
     * 与 [handleImageUpload] 一样由传输层在鉴权管线之外分流：配对时客户端还没有 token。
     * 失败一律返回中文提示，锁定中附带剩余秒数。
     */
    suspend fun handlePair(request: HostRequest): HostResponse {
        val text = request.body?.takeIf { it.isNotBlank() } ?: return invalidBody()
        val parsed = runCatching { ProtocolJson.decodeFromString<PairRequest>(text) }.getOrNull()
            ?: return invalidBody()
        if (parsed.deviceId.isBlank()) {
            return failure(400, ErrorCode.INVALID_ARGUMENT, "deviceId 不能为空")
        }
        if (!PairCodeManager.isValidFormat(parsed.code)) {
            return failure(400, ErrorCode.INVALID_ARGUMENT, "配对码必须是 6 位数字")
        }
        val deviceName = parsed.name.trim().ifBlank { "未命名设备" }
        return when (val verdict = pairing.verify(parsed.code, deviceName)) {
            PairVerdict.Ok -> {
                logInfo("配对成功：$deviceName（${parsed.deviceId}）")
                json(200, encodeJson(PairResponse(tokenProvider(), backend.currentVersion())))
            }

            is PairVerdict.Locked ->
                failure(401, ErrorCode.UNAUTHORIZED, "配对码已锁定，请 ${verdict.remainingSeconds} 秒后重试")

            PairVerdict.Invalid -> {
                logInfo("配对失败：配对码错误或已过期")
                failure(401, ErrorCode.UNAUTHORIZED, "配对码错误或已过期")
            }
        }
    }

    private suspend fun route(request: HostRequest): HostResponse {
        val method = request.method.uppercase()
        val seg = request.path.trim('/').split('/').filter { it.isNotEmpty() }
        return when {
            seg.size == 1 && seg[0] == "state" && method == "GET" ->
                json(200, encodeJson(backend.state()))

            seg.size == 1 && seg[0] == "categories" && method == "GET" ->
                json(200, encodeJson(backend.categories()))

            seg.size == 1 && seg[0] == "categories" && method == "POST" ->
                createCategory(request)

            seg.size == 2 && seg[0] == "categories" && method == "PATCH" ->
                patchCategory(request, seg[1])

            seg.size == 2 && seg[0] == "categories" && method == "DELETE" ->
                deleteCategory(seg[1])

            seg.size == 1 && seg[0] == "dishes" && method == "GET" ->
                json(200, encodeJson(backend.dishes()))

            seg.size == 1 && seg[0] == "dishes" && method == "POST" ->
                createDish(request)

            seg.size == 2 && seg[0] == "dishes" && method == "PATCH" ->
                patchDish(request, seg[1])

            seg.size == 2 && seg[0] == "dishes" && method == "DELETE" ->
                deleteDish(seg[1])

            seg.size == 3 && seg[0] == "dishes" && method == "POST" &&
                (seg[2] == "sell" || seg[2] == "unsell") ->
                sellOrUnsell(request, seg[1], seg[2] == "sell")

            seg.size == 2 && seg[0] == "images" && method == "GET" ->
                readImage(seg[1])

            seg.size == 2 && seg[0] == "menu" && seg[1] == "reset" && method == "POST" ->
                resetMenu()

            seg.size == 1 && seg[0] == "display" && method == "PATCH" ->
                patchDisplay(request)

            seg.size == 1 && seg[0] == "devices" && method == "GET" ->
                json(200, encodeJson(backend.devices()))

            else -> failure(404, ErrorCode.NOT_FOUND, "接口不存在")
        }
    }

    private suspend fun createCategory(request: HostRequest): HostResponse {
        val obj = parseBody(request) ?: return invalidBody()
        val body = decodeOrNull<CategoryCreateRequest>(obj)
            ?: return invalidBody()
        if (body.name.isBlank() || body.name.length > MAX_NAME_LENGTH) {
            return failure(400, ErrorCode.INVALID_ARGUMENT, "分类名需为 1-$MAX_NAME_LENGTH 字")
        }
        return idempotent(body.requestId) {
            json(201, encodeJson(backend.createCategory(body.name.trim(), body.sortOrder)))
        }
    }

    private suspend fun patchCategory(request: HostRequest, rawId: String): HostResponse {
        val id = rawId.toLongOrNull() ?: return notFound()
        val obj = parseBody(request) ?: return invalidBody()
        val body = decodeOrNull<CategoryPatchRequest>(obj) ?: return invalidBody()
        body.name?.let {
            if (it.isBlank() || it.length > MAX_NAME_LENGTH) {
                return failure(400, ErrorCode.INVALID_ARGUMENT, "分类名需为 1-$MAX_NAME_LENGTH 字")
            }
        }
        return idempotent(body.requestId) {
            when (val result = backend.patchCategory(id, body)) {
                is WriteResult.Ok -> json(200, encodeJson(result.value))
                WriteResult.NotFound -> notFound()
                WriteResult.Conflict -> conflict("分类已被删除")
            }
        }
    }

    private suspend fun deleteCategory(rawId: String): HostResponse {
        val id = rawId.toLongOrNull() ?: return notFound()
        return when (backend.deleteCategory(id)) {
            is WriteResult.Ok -> HostResponse.empty(204)
            WriteResult.NotFound -> notFound()
            WriteResult.Conflict -> conflict("分类已被删除")
        }
    }

    private suspend fun createDish(request: HostRequest): HostResponse {
        val obj = parseBody(request) ?: return invalidBody()
        val body = decodeOrNull<DishCreateRequest>(obj) ?: return invalidBody()
        validateDishFields(body.name, body.priceCents, body.unit, body.stockCount, body.description)
            ?.let { return it }
        return idempotent(body.requestId) {
            when (val result = backend.createDish(body)) {
                is WriteResult.Ok -> json(201, encodeJson(result.value))
                WriteResult.NotFound -> failure(400, ErrorCode.INVALID_ARGUMENT, "分类不存在或已停用")
                WriteResult.Conflict -> conflict("分类不存在或已停用")
            }
        }
    }

    private suspend fun patchDish(request: HostRequest, rawId: String): HostResponse {
        val id = rawId.toLongOrNull() ?: return notFound()
        val obj = parseBody(request) ?: return invalidBody()
        val body = decodeOrNull<DishPatchRequest>(obj) ?: return invalidBody()
        body.name?.let { name -> if (name.isBlank() || name.length > MAX_NAME_LENGTH) {
            return failure(400, ErrorCode.INVALID_ARGUMENT, "菜名需为 1-$MAX_NAME_LENGTH 字")
        } }
        body.priceCents?.let { if (it < 0 || it > MAX_PRICE_CENTS) {
            return failure(400, ErrorCode.INVALID_ARGUMENT, "价格需为 0-$MAX_PRICE_CENTS 分")
        } }
        body.unit?.let { if (it.length > MAX_UNIT_LENGTH) {
            return failure(400, ErrorCode.INVALID_ARGUMENT, "单位最多 $MAX_UNIT_LENGTH 字")
        } }
        body.stockCount?.let { if (it < 0) {
            return failure(400, ErrorCode.INVALID_ARGUMENT, "余量不能为负")
        } }
        body.description?.let { if (it.length > MAX_DESCRIPTION_LENGTH) {
            return failure(400, ErrorCode.INVALID_ARGUMENT, "描述最多 $MAX_DESCRIPTION_LENGTH 字")
        } }
        body.highlight?.let { raw ->
            if (raw.isNotEmpty() && DishHighlight.fromWire(raw) == null) {
                return failure(400, ErrorCode.INVALID_ARGUMENT, "菜品标签无效")
            }
        }
        return idempotent(body.requestId) {
            when (val result = backend.patchDish(id, body)) {
                is WriteResult.Ok -> json(200, encodeJson(result.value))
                WriteResult.NotFound -> notFound()
                WriteResult.Conflict -> conflict("菜品已被删除")
            }
        }
    }

    private suspend fun deleteDish(rawId: String): HostResponse {
        val id = rawId.toLongOrNull() ?: return notFound()
        return when (backend.deleteDish(id)) {
            is WriteResult.Ok -> HostResponse.empty(204)
            WriteResult.NotFound -> notFound()
            WriteResult.Conflict -> conflict("菜品已被删除")
        }
    }

    private suspend fun sellOrUnsell(request: HostRequest, rawId: String, sell: Boolean): HostResponse {
        val id = rawId.toLongOrNull() ?: return notFound()
        val obj = parseBody(request) ?: return invalidBody()
        val body = decodeOrNull<SellRequest>(obj) ?: return invalidBody()
        if (body.deviceId.isBlank()) {
            return failure(400, ErrorCode.INVALID_ARGUMENT, "deviceId 不能为空")
        }
        return idempotent(body.requestId) {
            val result = if (sell) backend.sell(id, body.deviceId) else backend.unsell(id, body.deviceId)
            when (result) {
                is WriteResult.Ok -> json(200, encodeJson(result.value))
                WriteResult.NotFound -> notFound()
                WriteResult.Conflict -> conflict(
                    if (sell) "已沽清或未启用库存，无法卖出" else "没有可撤销的卖出记录"
                )
            }
        }
    }

    private suspend fun readImage(name: String): HostResponse {
        if (!IMAGE_NAME.matches(name)) return notFound()
        val payload = backend.readImage(name) ?: return notFound()
        return HostResponse.bytes(200, payload.contentType, payload.bytes)
    }

    /**
     * `POST /menu/reset`：恢复默认菜单（`docs/design/0020` 第 2.6 节）。
     *
     * 仓储会发出 `FullReload`，传输层据此广播 `snapshot`，因此这里只需返回 204。
     */
    private suspend fun resetMenu(): HostResponse {
        backend.resetMenu()
        return HostResponse.empty(204)
    }

    /**
     * `PATCH /display`：修改展示设置（`docs/design/0023` 第 2.2 节）。
     *
     * 字段为 null 表示不改；`backgroundImagePath` 为空串表示清除自定义背景图。
     * 与菜单数据无关，不 bump `version`；广播由传输层在响应返回前发出。
     */
    private suspend fun patchDisplay(request: HostRequest): HostResponse {
        val obj = parseBody(request) ?: return invalidBody()
        val patch = decodeOrNull<DisplayPatchRequest>(obj) ?: return invalidBody()
        patch.backgroundScrimPercent?.let {
            if (it !in DisplaySpec.MIN_BACKGROUND_SCRIM_PERCENT..DisplaySpec.MAX_BACKGROUND_SCRIM_PERCENT) {
                return failure(
                    400,
                    ErrorCode.INVALID_ARGUMENT,
                    "蒙版透明度需为 ${DisplaySpec.MIN_BACKGROUND_SCRIM_PERCENT}-" +
                        "${DisplaySpec.MAX_BACKGROUND_SCRIM_PERCENT}"
                )
            }
        }
        patch.cardAlphaPercent?.let {
            if (it !in DisplaySpec.MIN_CARD_ALPHA_PERCENT..100) {
                return failure(
                    400,
                    ErrorCode.INVALID_ARGUMENT,
                    "卡片透明度需为 ${DisplaySpec.MIN_CARD_ALPHA_PERCENT}-100"
                )
            }
        }
        return json(200, encodeJson(backend.applyDisplay(patch)))
    }

    private fun validateDishFields(
        name: String,
        priceCents: Int,
        unit: String,
        stockCount: Int,
        description: String
    ): HostResponse? = when {
        name.isBlank() || name.length > MAX_NAME_LENGTH ->
            failure(400, ErrorCode.INVALID_ARGUMENT, "菜名需为 1-$MAX_NAME_LENGTH 字")

        priceCents < 0 || priceCents > MAX_PRICE_CENTS ->
            failure(400, ErrorCode.INVALID_ARGUMENT, "价格需为 0-$MAX_PRICE_CENTS 分")

        unit.length > MAX_UNIT_LENGTH ->
            failure(400, ErrorCode.INVALID_ARGUMENT, "单位最多 $MAX_UNIT_LENGTH 字")

        stockCount < 0 -> failure(400, ErrorCode.INVALID_ARGUMENT, "余量不能为负")

        description.length > MAX_DESCRIPTION_LENGTH ->
            failure(400, ErrorCode.INVALID_ARGUMENT, "描述最多 $MAX_DESCRIPTION_LENGTH 字")

        else -> null
    }

    private suspend fun idempotent(
        requestId: String?,
        block: suspend () -> HostResponse
    ): HostResponse {
        if (requestId.isNullOrBlank()) return block()
        synchronized(cacheLock) { idempotency[requestId]?.let { return it } }
        val response = block()
        if (response.status < 500) {
            synchronized(cacheLock) {
                idempotency[requestId] = response
                while (idempotency.size > cacheCapacity) {
                    val oldest = idempotency.keys.firstOrNull() ?: break
                    idempotency.remove(oldest)
                }
            }
        }
        return response
    }

    private fun parseBody(request: HostRequest): JsonObject? {
        val text = request.body?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { ProtocolJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
    }

    private inline fun <reified T> decodeOrNull(obj: JsonObject): T? =
        runCatching { ProtocolJson.decodeFromJsonElement<T>(obj) }.getOrNull()

    private inline fun <reified T> encodeJson(value: T): String = ProtocolJson.encodeToString(value)

    private fun invalidBody() = failure(400, ErrorCode.INVALID_ARGUMENT, "请求体格式错误")

    private fun unauthorized() = failure(401, ErrorCode.UNAUTHORIZED, "配对码缺失或已失效")

    private fun notFound() = failure(404, ErrorCode.NOT_FOUND, "对象不存在")

    private fun conflict(message: String) = failure(409, ErrorCode.CONFLICT, message)

    private fun failure(status: Int, code: String, message: String): HostResponse =
        HostResponse.json(status, """{"error":{"code":"$code","message":"$message"}}""")

    private fun internalError(path: String, e: Exception): HostResponse {
        val logId = "%06x".format((System.currentTimeMillis() and 0xFFFFFF))
        logError("主机处理 $path 失败（日志 ID $logId）", e)
        return failure(500, ErrorCode.INTERNAL, "服务器内部错误（日志 ID $logId）")
    }

    private fun json(status: Int, body: String): HostResponse = HostResponse.json(status, body)

    private companion object {
        const val IDEMPOTENCY_CAPACITY = 200
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        /** `POST /images?kind=background`：按背景图规格落盘（`docs/design/0023` 第 2.3 节）。 */
        const val IMAGE_KIND_BACKGROUND = "background"
        const val MAX_NAME_LENGTH = 20
        const val MAX_PRICE_CENTS = 999_999
        const val MAX_UNIT_LENGTH = 6
        const val MAX_DESCRIPTION_LENGTH = 100
        val IMAGE_NAME = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|png)")
    }
}
