package com.tanpai.menu.host

import com.tanpai.menu.net.protocol.CategoryDto
import com.tanpai.menu.net.protocol.CategoryPatchRequest
import com.tanpai.menu.net.protocol.DeviceDto
import com.tanpai.menu.net.protocol.DishCreateRequest
import com.tanpai.menu.net.protocol.DishDto
import com.tanpai.menu.net.protocol.DishPatchRequest
import com.tanpai.menu.net.protocol.StateDto

/**
 * 主机 HTTP 请求的容器无关表示（`docs/design/0015` 第 2.1 节）。
 *
 * [path] 为去掉 `/api/v1` 前缀后的路径，例如 `/dishes/3/sell`。
 */
class HostRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
) {
    /** `Authorization: Bearer <token>` 中的 token，缺失返回 null。 */
    val bearerToken: String?
        get() = headers.entries
            .firstOrNull { it.key.equals("authorization", ignoreCase = true) }
            ?.value
            ?.trim()
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

/** 主机 HTTP 响应。 */
class HostResponse(
    val status: Int,
    val contentType: String,
    val body: ByteArray
) {
    val bodyText: String get() = String(body, Charsets.UTF_8)

    companion object {
        const val JSON = "application/json; charset=utf-8"

        fun json(status: Int, body: String): HostResponse =
            HostResponse(status, JSON, body.toByteArray(Charsets.UTF_8))

        fun bytes(status: Int, contentType: String, body: ByteArray): HostResponse =
            HostResponse(status, contentType, body)

        fun empty(status: Int): HostResponse = HostResponse(status, JSON, ByteArray(0))
    }
}

/** 写命令结果：成功 / 目标不存在 / 业务冲突。 */
sealed interface WriteResult<out T> {
    data class Ok<T>(val value: T) : WriteResult<T>
    data object NotFound : WriteResult<Nothing>
    data object Conflict : WriteResult<Nothing>
}

/** 图片字节与内容类型。 */
class ImagePayload(val bytes: ByteArray, val contentType: String)

/**
 * 主机管线的业务窄接口：生产实现见 `RepositoryMenuBackend`，测试可用内存替身替换。
 *
 * 列表语义遵循契约「软删除记录不出现在快照与列表中」，实现侧只返回 `enabled = true` 的数据。
 */
interface MenuBackend {
    /** 当前全局版本号；配对响应与设备上下线事件使用，避免重复构建完整快照。 */
    suspend fun currentVersion(): Long

    suspend fun state(): StateDto

    suspend fun categories(): List<CategoryDto>

    suspend fun createCategory(name: String, sortOrder: Int): CategoryDto

    suspend fun patchCategory(id: Long, patch: CategoryPatchRequest): WriteResult<CategoryDto>

    suspend fun deleteCategory(id: Long): WriteResult<Unit>

    suspend fun dishes(): List<DishDto>

    suspend fun createDish(request: DishCreateRequest): WriteResult<DishDto>

    suspend fun patchDish(id: Long, patch: DishPatchRequest): WriteResult<DishDto>

    suspend fun deleteDish(id: Long): WriteResult<Unit>

    suspend fun sell(dishId: Long, deviceId: String): WriteResult<DishDto>

    suspend fun unsell(dishId: Long, deviceId: String): WriteResult<DishDto>

    suspend fun devices(): List<DeviceDto>

    /** 保存上传图片（解码 + 压缩 + 落盘），返回相对路径；解码失败返回 null。 */
    suspend fun saveImage(bytes: ByteArray): String?

    /** 读取图片字节；文件不存在返回 null。 */
    suspend fun readImage(name: String): ImagePayload?

    /**
     * 清空菜单与图片并写回默认菜单（`docs/design/0020` 第 2.6 节）。
     *
     * 由仓储发出 `FullReload`，传输层按既有规则改广播 `snapshot`，两端重新对齐。
     */
    suspend fun resetMenu()
}
