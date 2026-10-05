package com.tanpai.menu.net.protocol

import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractTest {

    @Test
    fun iso8601FormatsUtcWithMilliseconds() {
        assertEquals("1970-01-01T00:00:00.000Z", Iso8601.format(0L))
        assertEquals("2026-10-05T03:20:30.123Z", Iso8601.format(1791170430123L))
    }

    @Test
    fun iso8601RoundTrips() {
        val millis = 1791170430123L
        assertEquals(millis, Iso8601.parseOrNull(Iso8601.format(millis)))
    }

    @Test
    fun iso8601RejectsMalformedText() {
        assertNull(Iso8601.parseOrNull("2026-10-05 03:20:30"))
        assertNull(Iso8601.parseOrNull("not-a-time"))
    }

    @Test
    fun stateUsesContractFieldNames() {
        val text = ProtocolJson.encodeToString(
            StateDto.serializer(),
            StateDto(
                version = 12,
                categories = listOf(CategoryDto(1, "热菜", 1, true, "1970-01-01T00:00:00.000Z")),
                dishes = emptyList()
            )
        )
        assertTrue(text.contains("\"version\":12"))
        assertTrue(text.contains("\"sortOrder\":1"))
        assertTrue(text.contains("\"updatedAt\":\"1970-01-01T00:00:00.000Z\""))
    }

    @Test
    fun categoryRoundTripsThroughDto() {
        val entity = Category(id = 7, name = "饮品", sortOrder = 2, enabled = false, updatedAt = 1000L)
        assertEquals(entity, entity.toDto().toEntity())
    }

    @Test
    fun dishRoundTripsThroughDtoKeepingNullableImage() {
        val entity = Dish(
            id = 3, categoryId = 1, name = "烤串", priceCents = 300, unit = "串",
            imagePath = null, stockEnabled = true, stockCount = 0, soldOut = true,
            description = "微辣", sortOrder = 5, enabled = false, updatedAt = 2000L
        )
        assertEquals(entity, entity.toDto().toEntity())
    }

    @Test
    fun dishDtoKeepsImagePathWhenPresent() {
        val entity = Dish(
            id = 4, categoryId = 1, name = "花甲", priceCents = 1200,
            imagePath = "images/abc.jpg", updatedAt = 3000L
        )
        val dto = entity.toDto()
        assertEquals("images/abc.jpg", dto.imagePath)
        assertEquals(entity, dto.toEntity())
    }

    @Test
    fun errorBodyMatchesContractShape() {
        val text = ProtocolJson.encodeToString(
            ErrorBody.serializer(),
            ErrorBody(ErrorInfo(ErrorCode.CONFLICT, "已沽清"))
        )
        assertEquals("{\"error\":{\"code\":\"conflict\",\"message\":\"已沽清\"}}", text)
    }

    @Test
    fun wireEventCarriesTypeVersionAndPayload() {
        val text = ProtocolJson.encodeToString(
            WireEvent.serializer(),
            WireEvent(
                type = EventType.DISH_UPDATED,
                version = 13,
                payload = JsonObject(mapOf("id" to JsonPrimitive(3)))
            )
        )
        assertTrue(text.contains("\"type\":\"dish.updated\""))
        assertTrue(text.contains("\"version\":13"))
        assertTrue(text.contains("\"payload\":{\"id\":3}"))
    }

    @Test
    fun snapshotEventDefaultsToEmptyPayload() {
        val text = ProtocolJson.encodeToString(
            WireEvent.serializer(),
            WireEvent(type = EventType.SNAPSHOT, version = 1)
        )
        assertEquals("{\"type\":\"snapshot\",\"version\":1,\"payload\":{}}", text)
    }

    @Test
    fun patchRequestDecodesAbsentFieldsAsNull() {
        val patch = ProtocolJson.decodeFromString(
            DishPatchRequest.serializer(),
            "{\"priceCents\":500}"
        )
        assertNull(patch.name)
        assertNull(patch.imagePath)
        assertEquals(500, patch.priceCents)
    }

    @Test
    fun sellRequestRequiresDeviceId() {
        val request = ProtocolJson.decodeFromString(
            SellRequest.serializer(),
            "{\"deviceId\":\"phone-a\",\"requestId\":\"r-1\"}"
        )
        assertEquals("phone-a", request.deviceId)
        assertEquals("r-1", request.requestId)
    }
}