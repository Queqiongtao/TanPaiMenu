package com.tanpai.menu.host

import com.tanpai.menu.data.MenuChange
import com.tanpai.menu.net.protocol.DeviceDto
import com.tanpai.menu.net.protocol.DisplaySettingsDto
import com.tanpai.menu.net.protocol.EventType
import com.tanpai.menu.net.protocol.ProtocolJson
import com.tanpai.menu.net.protocol.StateDto
import com.tanpai.menu.net.protocol.WireEvent
import com.tanpai.menu.net.protocol.toDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * WS 连接注册表与事件帧构造（`docs/design/0015` 第 2.5 节）。
 *
 * 只负责「有哪些连接」与「把 [MenuChange] 变成线报文」；设备注册表写入、快照拉取等业务
 * 由 `HostServer` 在连接生命周期内完成，本类因此可脱离 NanoHTTPD 单测。
 */
class WsHub {

    /** 已鉴权连接的最小接口，与传输实现解耦，便于测试与替换。 */
    interface Connection {
        val deviceId: String
        val name: String
        val role: String

        /** 连接建立时间（System.currentTimeMillis()），用于「已连接多久」展示。 */
        val connectedAt: Long

        /** 推送一个文本帧；连接已关闭或发送失败返回 false。 */
        fun push(frame: String): Boolean

        /** 发送一个 WS ping；连接已关闭或发送失败返回 false。 */
        fun sendPing(): Boolean

        /** 主动断开并携带关闭码（`4401` 表示配对码失效）。 */
        fun close(code: Int, reason: String)
    }

    private val connections = CopyOnWriteArrayList<Connection>()

    fun add(connection: Connection) {
        connections.add(connection)
    }

    fun remove(connection: Connection) {
        connections.remove(connection)
    }

    fun size(): Int = connections.size

    /** 当前在线连接快照（按连接时间升序）；设备列表只读使用。 */
    fun connections(): List<Connection> = connections.sortedBy { it.connectedAt }

    /**
     * 广播事件帧；[except] 用于排除触发者自身（如新连接不必收到自己的 `device.joined`）。
     * 发送失败的连接立即移除，返回成功发送的连接数。
     */
    fun broadcast(event: WireEvent, except: Connection? = null): Int {
        val frame = encode(event)
        var sent = 0
        connections.forEach { connection ->
            if (connection === except) return@forEach
            if (connection.push(frame)) sent++ else connections.remove(connection)
        }
        return sent
    }

    /**
     * 向所有连接发心跳，返回存活连接数（`docs/design/0016` 第 2.4 节）。
     *
     * 存活判定依赖客户端按协议回 pong：读超时会被 NanoWSD 判为异常并关闭连接，
     * 这里只负责把发送失败的连接摘掉。
     */
    fun pingAll(): Int {
        var alive = 0
        connections.forEach { connection ->
            if (connection.sendPing()) alive++ else connections.remove(connection)
        }
        return alive
    }

    /** 断开全部连接（更换配对码后旧连接立即失效）。 */
    fun closeAll(code: Int, reason: String) {
        connections.forEach { runCatching { it.close(code, reason) } }
        connections.clear()
    }

    /** 首帧快照（payload 同 `/state`）。 */
    fun snapshotEvent(state: StateDto): WireEvent =
        event(EventType.SNAPSHOT, state.version, state)

    /** 增量事件帧；[MenuChange.FullReload] 无增量语义，由调用方改发 `snapshot`。 */
    fun changeEvent(change: MenuChange): WireEvent? = when (change) {
        is MenuChange.DishUpdated ->
            event(EventType.DISH_UPDATED, change.version, change.dish.toDto())

        is MenuChange.DishDeleted ->
            idEvent(EventType.DISH_DELETED, change.version, change.dishId)

        is MenuChange.CategoryUpdated ->
            event(EventType.CATEGORY_UPDATED, change.version, change.category.toDto())

        is MenuChange.CategoryDeleted ->
            idEvent(EventType.CATEGORY_DELETED, change.version, change.categoryId)

        is MenuChange.FullReload -> null

        // 展示设置事件由 HostServer 用 displayEvent 构造（载荷是完整设置，不在 MenuChange 里）。
        is MenuChange.DisplayUpdated -> null
    }

    /** 设备上下线事件帧。 */
    fun deviceEvent(type: String, version: Long, device: DeviceDto): WireEvent =
        event(type, version, device)

    /** 展示设置变更帧（`docs/design/0023` 第 2.2 节）。 */
    fun displayEvent(settings: DisplaySettingsDto, version: Long): WireEvent =
        event(EventType.DISPLAY_UPDATED, version, settings)

    fun encode(event: WireEvent): String = ProtocolJson.encodeToString(event)

    private fun idEvent(type: String, version: Long, id: Long): WireEvent =
        WireEvent(type, version, JsonObject(mapOf("id" to JsonPrimitive(id))))

    private inline fun <reified T> event(type: String, version: Long, payload: T): WireEvent =
        WireEvent(type, version, ProtocolJson.encodeToJsonElement(payload).jsonObject)
}