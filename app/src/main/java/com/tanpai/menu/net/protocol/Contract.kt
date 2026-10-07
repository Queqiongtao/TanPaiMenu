package com.tanpai.menu.net.protocol

import com.tanpai.menu.data.db.DishHighlight
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 契约 JSON 编解码配置。
 *
 * - `encodeDefaults = true`：可空字段也显式输出（如 `imagePath: null`），与 docs/design/0001 的报文一致。
 * - 未知字段直接报错，防止契约漂移被静默忽略。
 */
val ProtocolJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
}

/** WS 事件类型，取值见 docs/design/0001 第 4 节。 */
object EventType {
    const val SNAPSHOT = "snapshot"
    const val DISH_UPDATED = "dish.updated"
    const val DISH_DELETED = "dish.deleted"
    const val CATEGORY_UPDATED = "category.updated"
    const val CATEGORY_DELETED = "category.deleted"
    const val DEVICE_JOINED = "device.joined"
    const val DEVICE_LEFT = "device.left"
}

/** 统一错误码，见 docs/design/0001 第 3.1 节。 */
object ErrorCode {
    const val INVALID_ARGUMENT = "invalid_argument"
    const val UNAUTHORIZED = "unauthorized"
    const val FORBIDDEN = "forbidden"
    const val NOT_FOUND = "not_found"
    const val CONFLICT = "conflict"
    const val PAYLOAD_TOO_LARGE = "payload_too_large"
    const val INTERNAL = "internal"
}

/** 设备角色。 */
object DeviceRole {
    const val HOST = "host"
    const val CLIENT = "client"
}

@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val enabled: Boolean = true,
    val updatedAt: String
)

@Serializable
data class DishDto(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val priceCents: Int,
    val unit: String = "份",
    val imagePath: String? = null,
    val stockEnabled: Boolean = false,
    val stockCount: Int = 0,
    val soldOut: Boolean = false,
    val highlight: DishHighlight? = null,
    val description: String = "",
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val updatedAt: String
)

/** GET /state 全量快照。 */
@Serializable
data class StateDto(
    val version: Long,
    val categories: List<CategoryDto> = emptyList(),
    val dishes: List<DishDto> = emptyList(),
    /** 店名；空串或 null 表示未设置，客户端回退到默认文案。 */
    val storeName: String? = null,
    /** 主机是否允许手机编辑（`docs/design/0020` 第 2.3 节）；老客户端忽略该字段。 */
    val clientEditsEnabled: Boolean = true
)

/** GET /devices 列表项。 */
@Serializable
data class DeviceDto(
    val id: String,
    val name: String,
    val role: String = DeviceRole.CLIENT,
    val lastSeen: String
)

/** POST /images 响应。 */
@Serializable
data class ImageUploadDto(
    val imagePath: String,
    val url: String
)

@Serializable
data class ErrorInfo(val code: String, val message: String)

@Serializable
data class ErrorBody(val error: ErrorInfo)

/** WS 报文；`payload` 为对应实体的 JSON，快照帧为 /state 结构。 */
@Serializable
data class WireEvent(
    val type: String,
    val version: Long,
    val payload: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class CategoryCreateRequest(
    val name: String,
    val sortOrder: Int = 0,
    val requestId: String? = null
)

/**
 * 分类局部更新：字段为 null 表示不修改。
 */
@Serializable
data class CategoryPatchRequest(
    val name: String? = null,
    val sortOrder: Int? = null,
    val enabled: Boolean? = null,
    val requestId: String? = null
)

@Serializable
data class DishCreateRequest(
    val categoryId: Long,
    val name: String,
    val priceCents: Int,
    val unit: String = "份",
    val imagePath: String? = null,
    val stockEnabled: Boolean = false,
    val stockCount: Int = 0,
    val highlight: DishHighlight? = null,
    val description: String = "",
    val sortOrder: Int = 0,
    val requestId: String? = null
)

/**
 * 菜品局部更新：字段为 null 表示不修改；`imagePath` 传空串表示清空图片。
 */
@Serializable
data class DishPatchRequest(
    val categoryId: Long? = null,
    val name: String? = null,
    val priceCents: Int? = null,
    val unit: String? = null,
    val imagePath: String? = null,
    val stockEnabled: Boolean? = null,
    val stockCount: Int? = null,
    val soldOut: Boolean? = null,
    /** null 不修改，空串清空，否则为 DishHighlight.wireValue。 */
    val highlight: String? = null,
    val description: String? = null,
    val sortOrder: Int? = null,
    val enabled: Boolean? = null,
    val requestId: String? = null
)

@Serializable
data class SellRequest(
    val deviceId: String,
    val requestId: String? = null
)

/**
 * `POST /pair` 请求：用一次性 6 位配对码换取长期 token（`docs/design/0019` 第 3 节）。
 *
 * 该接口不需要 `Authorization`，是唯一在鉴权管线之外的写入口。
 */
@Serializable
data class PairRequest(
    val code: String,
    val deviceId: String,
    val name: String
)

/** `POST /pair` 响应：配对成功后客户端持有的凭据与当前主机版本。 */
@Serializable
data class PairResponse(
    val token: String,
    val version: Long
)

/** 删除类事件的 payload：`{"id":<id>}`。 */
@Serializable
data class IdPayload(
    val id: Long
)
