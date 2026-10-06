package com.tanpai.menu.host

import com.tanpai.menu.data.MenuChange
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.net.protocol.DeviceDto
import com.tanpai.menu.net.protocol.EventType
import com.tanpai.menu.net.protocol.ProtocolJson
import com.tanpai.menu.net.protocol.StateDto
import com.tanpai.menu.net.protocol.WireEvent
import com.tanpai.menu.net.protocol.toDto
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WS 事件帧构造与连接注册表语义（纯 JVM）。 */
class WsHubTest {

    @Test
    fun snapshotEventCarriesStatePayload() {
        val hub = WsHub()
        val decoded = decode(hub.encode(hub.snapshotEvent(StateDto(version = 3, dishes = listOf(dishDto())))))
        assertEquals(EventType.SNAPSHOT, decoded.type)
        assertEquals(3L, decoded.version)
        assertTrue(decoded.payload.containsKey("dishes"))
    }

    @Test
    fun dishUpdatedMapsToContractEvent() {
        val hub = WsHub()
        val event = hub.changeEvent(MenuChange.DishUpdated(9L, dishEntity()))!!
        val decoded = decode(hub.encode(event))
        assertEquals(EventType.DISH_UPDATED, decoded.type)
        assertEquals(9L, decoded.version)
        assertEquals(10L, decoded.payload["id"]!!.jsonPrimitive.long)
    }

    @Test
    fun dishDeletedCarriesIdPayload() {
        val hub = WsHub()
        val decoded = decode(hub.encode(hub.changeEvent(MenuChange.DishDeleted(5L, 42L))!!))
        assertEquals(EventType.DISH_DELETED, decoded.type)
        assertEquals(42L, decoded.payload["id"]!!.jsonPrimitive.long)
    }

    @Test
    fun categoryDeletedCarriesIdPayload() {
        val hub = WsHub()
        val decoded = decode(hub.encode(hub.changeEvent(MenuChange.CategoryDeleted(6L, 7L))!!))
        assertEquals(EventType.CATEGORY_DELETED, decoded.type)
        assertEquals(7L, decoded.payload["id"]!!.jsonPrimitive.long)
    }

    @Test
    fun fullReloadHasNoIncrementalFrame() {
        assertNull(WsHub().changeEvent(MenuChange.FullReload(11L)))
    }

    @Test
    fun connectionsSnapshotIsSortedByConnectedAt() {
        val hub = WsHub()
        hub.add(FakeConnection("late", connectedAt = 2_000L))
        hub.add(FakeConnection("early", connectedAt = 1_000L))

        val online = hub.connections()

        assertEquals(listOf("early", "late"), online.map { it.deviceId })
        assertEquals(listOf(1_000L, 2_000L), online.map { it.connectedAt })
    }

    @Test
    fun broadcastSkipsSenderAndDropsBrokenConnections() {
        val hub = WsHub()
        val sender = FakeConnection("a")
        val broken = FakeConnection("b", fail = true)
        val healthy = FakeConnection("c")
        hub.add(sender)
        hub.add(broken)
        hub.add(healthy)

        val sent = hub.broadcast(
            hub.deviceEvent(EventType.DEVICE_JOINED, 1L, DeviceDto("a", "手机A", "client", STAMP)),
            except = sender
        )

        assertEquals(1, sent)
        assertTrue(sender.frames.isEmpty())
        assertEquals(1, healthy.frames.size)
        assertEquals(2, hub.size())
    }

    @Test
    fun pingAllDropsConnectionsThatFailToSend() {
        val hub = WsHub()
        hub.add(FakeConnection("a"))
        hub.add(FakeConnection("b", fail = true))

        val alive = hub.pingAll()

        assertEquals(1, alive)
        assertEquals(listOf("a"), hub.connections().map { it.deviceId })
    }

    @Test
    fun closeAllDropsEveryConnectionWithGivenCode() {
        val hub = WsHub()
        val connection = FakeConnection("a")
        hub.add(connection)

        hub.closeAll(4401, "配对码已更换")

        assertEquals(0, hub.size())
        assertEquals(4401, connection.closeCode)
    }

    private fun decode(text: String): WireEvent =
        ProtocolJson.decodeFromString(WireEvent.serializer(), text)

    private fun dishEntity() = Dish(
        id = 10,
        categoryId = 1,
        name = "烤串",
        priceCents = 300,
        updatedAt = 1000L
    )

    private fun dishDto() = dishEntity().toDto()

    private class FakeConnection(
        override val deviceId: String,
        override val connectedAt: Long = 0L,
        private val fail: Boolean = false
    ) : WsHub.Connection {
        override val name: String = "设备-$deviceId"
        override val role: String = "client"
        val frames = mutableListOf<String>()
        var closeCode: Int? = null

        override fun push(frame: String): Boolean {
            if (fail) return false
            frames += frame
            return true
        }

        override fun sendPing(): Boolean = !fail

        override fun close(code: Int, reason: String) {
            closeCode = code
        }
    }

    private companion object {
        const val STAMP = "2026-10-06T00:00:00.000Z"
    }
}