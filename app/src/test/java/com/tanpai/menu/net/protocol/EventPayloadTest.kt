package com.tanpai.menu.net.protocol

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** WS 事件 payload 解码单测（`docs/design/0019` 第 2.2 节）：可解释才返回，否则交给上层重连。 */
class EventPayloadTest {

    private fun event(type: String, version: Long, payload: String) = WireEvent(
        type = type,
        version = version,
        payload = ProtocolJson.parseToJsonElement(payload).jsonObject
    )

    @Test
    fun idPayloadIsDecoded() {
        val decoded = event(EventType.DISH_DELETED, 3, """{"id":5}""").payloadAs<IdPayload>()
        assertEquals(IdPayload(5), decoded)
    }

    @Test
    fun dishPayloadIsDecoded() {
        val payload = """
            {"id":10,"categoryId":1,"name":"馄饨","priceCents":1000,"unit":"份","imagePath":null,
             "stockEnabled":false,"stockCount":0,"soldOut":false,"description":"","sortOrder":1,
             "enabled":true,"updatedAt":"2026-10-06T00:00:00.000Z"}
        """.trimIndent()
        val decoded = event(EventType.DISH_UPDATED, 4, payload).payloadAs<DishDto>()
        assertEquals(10L, decoded?.id)
        assertEquals(1000, decoded?.priceCents)
    }

    @Test
    fun unknownFieldFailsFast() {
        assertNull(event(EventType.DISH_DELETED, 3, """{"id":5,"extra":1}""").payloadAs<IdPayload>())
    }

    @Test
    fun missingFieldFailsFast() {
        assertNull(event(EventType.DISH_DELETED, 3, """{}""").payloadAs<IdPayload>())
    }
}