package com.tanpai.menu.client

import com.tanpai.menu.net.protocol.CategoryDto
import com.tanpai.menu.net.protocol.DishDto
import com.tanpai.menu.net.protocol.DisplaySettingsDto
import com.tanpai.menu.net.protocol.EventType
import com.tanpai.menu.net.protocol.ProtocolJson
import com.tanpai.menu.net.protocol.StateDto
import com.tanpai.menu.net.protocol.WireEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 客户端内存镜像单测（`docs/design/0019` 第 2.3、5 节）：
 * 快照全量替换、按版本丢弃旧事件、增量合并、删除与分类删除级联、坏报文要求重同步。
 */
class ClientMirrorTest {

    private val mirror = ClientMirror()

    private fun category(id: Long, sortOrder: Int = 0) =
        CategoryDto(id = id, name = "分类$id", sortOrder = sortOrder, updatedAt = STAMP)

    private fun dish(id: Long, categoryId: Long, priceCents: Int = 1000, sortOrder: Int = 0) =
        DishDto(
            id = id,
            categoryId = categoryId,
            name = "菜$id",
            priceCents = priceCents,
            sortOrder = sortOrder,
            updatedAt = STAMP
        )

    private fun event(type: String, version: Long, payload: JsonObject = JsonObject(emptyMap())) =
        WireEvent(type = type, version = version, payload = payload)

    private fun payloadOf(value: Any): JsonObject = when (value) {
        is DishDto -> ProtocolJson.encodeToJsonElement(DishDto.serializer(), value).jsonObject
        is CategoryDto -> ProtocolJson.encodeToJsonElement(CategoryDto.serializer(), value).jsonObject
        is Long -> ProtocolJson.parseToJsonElement("""{"id":$value}""").jsonObject
        else -> error("不支持的 payload")
    }

    @Test
    fun snapshotReplacesEverything() {
        mirror.applySnapshot(
            StateDto(5, listOf(category(1), category(2)), listOf(dish(10, 1), dish(11, 2)))
        )
        assertEquals(5L, mirror.version)
        assertEquals(2, mirror.categories().size)
        assertEquals(2, mirror.dishes().size)

        mirror.applySnapshot(StateDto(9, listOf(category(3)), listOf(dish(12, 3))))

        assertEquals(9L, mirror.version)
        assertEquals(listOf(3L), mirror.categories().map { it.id })
        assertEquals(listOf(12L), mirror.dishes().map { it.id })
    }

    @Test
    fun displaySettingsComeFromSnapshotAndEvent() {
        val settings = DisplaySettingsDto(
            backgroundStyleId = "bamboo",
            backgroundScrimPercent = 40,
            cardAlphaPercent = 70,
            showStockHint = false
        )
        mirror.applySnapshot(StateDto(5, emptyList(), emptyList(), display = settings))
        assertEquals(settings, mirror.display)

        // display.updated 与菜单版本无关：即便版本不前进也必须应用（docs/design/0023 第 2.2 节）。
        val updated = DisplaySettingsDto(backgroundImagePath = "images/a.jpg")
        val applied = mirror.applyEvent(
            event(
                EventType.DISPLAY_UPDATED,
                5,
                ProtocolJson.encodeToJsonElement(DisplaySettingsDto.serializer(), updated).jsonObject
            )
        )

        assertEquals(EventApplyResult.APPLIED, applied)
        assertEquals("images/a.jpg", mirror.display.backgroundImagePath)
    }

    @Test
    fun entitiesAreSortedBySortOrderThenId() {
        mirror.applySnapshot(
            StateDto(
                version = 1,
                categories = listOf(category(2, 1), category(1, 1), category(3, 0)),
                dishes = listOf(dish(20, 1, sortOrder = 2), dish(10, 1, sortOrder = 1))
            )
        )

        assertEquals(listOf(3L, 1L, 2L), mirror.categories().map { it.id })
        assertEquals(listOf(10L, 20L), mirror.dishes().map { it.id })
        assertEquals(listOf(10L, 20L), mirror.dishesOf(1).map { it.id })
    }

    @Test
    fun sameOrOlderVersionIsDiscarded() {
        mirror.applySnapshot(StateDto(5, listOf(category(1)), listOf(dish(10, 1))))

        val result = mirror.applyEvent(
            event(EventType.DISH_UPDATED, 5, payloadOf(dish(10, 1, priceCents = 9999)))
        )

        assertEquals(EventApplyResult.APPLIED, result)
        assertEquals(1000, mirror.dishes().first().priceCents)
        assertEquals(5L, mirror.version)
    }

    @Test
    fun newerDishEventIsMerged() {
        mirror.applySnapshot(StateDto(5, listOf(category(1)), listOf(dish(10, 1))))

        val result = mirror.applyEvent(
            event(EventType.DISH_UPDATED, 6, payloadOf(dish(10, 1, priceCents = 2500)))
        )

        assertEquals(EventApplyResult.APPLIED, result)
        assertEquals(2500, mirror.dishes().first().priceCents)
        assertEquals(6L, mirror.version)
    }

    @Test
    fun categoryEventAddsNewCategory() {
        mirror.applySnapshot(StateDto(5, listOf(category(1)), listOf(dish(10, 1))))

        mirror.applyEvent(event(EventType.CATEGORY_UPDATED, 6, payloadOf(category(2))))

        assertEquals(listOf(1L, 2L), mirror.categories().map { it.id })
    }

    @Test
    fun dishDeletedRemovesDish() {
        mirror.applySnapshot(StateDto(5, listOf(category(1)), listOf(dish(10, 1), dish(11, 1))))

        mirror.applyEvent(event(EventType.DISH_DELETED, 6, payloadOf(10L)))

        assertEquals(listOf(11L), mirror.dishes().map { it.id })
    }

    @Test
    fun categoryDeletedCascadesToDishes() {
        mirror.applySnapshot(
            StateDto(5, listOf(category(1), category(2)), listOf(dish(10, 1), dish(11, 1), dish(12, 2)))
        )

        mirror.applyEvent(event(EventType.CATEGORY_DELETED, 6, payloadOf(1L)))

        assertEquals(listOf(2L), mirror.categories().map { it.id })
        assertEquals(listOf(12L), mirror.dishes().map { it.id })
    }

    @Test
    fun deviceEventsAreIgnoredWithoutVersionBump() {
        mirror.applySnapshot(StateDto(5, listOf(category(1)), listOf(dish(10, 1))))

        val result = mirror.applyEvent(event(EventType.DEVICE_JOINED, 99))

        assertEquals(EventApplyResult.APPLIED, result)
        assertEquals(5L, mirror.version)
    }

    @Test
    fun snapshotEventReplacesAll() {
        mirror.applySnapshot(StateDto(5, listOf(category(1)), listOf(dish(10, 1))))

        val state = StateDto(8, listOf(category(2)), listOf(dish(20, 2)))
        val json = ProtocolJson.encodeToJsonElement(StateDto.serializer(), state).jsonObject
        val result = mirror.applyEvent(event(EventType.SNAPSHOT, 8, json))

        assertEquals(EventApplyResult.APPLIED, result)
        assertEquals(8L, mirror.version)
        assertEquals(listOf(20L), mirror.dishes().map { it.id })
    }

    @Test
    fun unparsablePayloadNeedsResync() {
        mirror.applySnapshot(StateDto(5, listOf(category(1)), listOf(dish(10, 1))))

        val result = mirror.applyEvent(
            event(EventType.DISH_UPDATED, 6, ProtocolJson.parseToJsonElement("""{"id":10}""").jsonObject)
        )

        assertEquals(EventApplyResult.NEED_RESYNC, result)
    }

    @Test
    fun clearResetsVersionAndData() {
        mirror.applySnapshot(StateDto(5, listOf(category(1)), listOf(dish(10, 1))))

        mirror.clear()

        assertEquals(0L, mirror.version)
        assertTrue(mirror.categories().isEmpty())
        assertTrue(mirror.dishes().isEmpty())
    }

    private companion object {
        const val STAMP = "2026-10-06T00:00:00.000Z"
    }
}