package com.tanpai.menu.backup

import com.tanpai.menu.data.db.DishHighlight

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val quote = '"'

    private fun category(id: Long = 1L) = BackupCategory(
        id = id,
        name = "热菜",
        sortOrder = 0,
        enabled = true,
        updatedAt = 1_700_000_000_000L
    )

    private fun dish(id: Long = 10L, categoryId: Long = 1L) = BackupDish(
        id = id,
        categoryId = categoryId,
        name = "烤串",
        priceCents = 300,
        unit = "串",
        imagePath = "images/3b5128dc-fe48-44e5-abba-753cc1a3aaa9.jpg",
        stockEnabled = true,
        stockCount = 18,
        highlight = DishHighlight.Recommended,
        updatedAt = 1_700_000_000_000L
    )

    private fun backup(
        format: Int = BackupCodec.FORMAT,
        app: String = BackupCodec.APP_NAME,
        categories: List<BackupCategory> = listOf(category()),
        dishes: List<BackupDish> = listOf(dish())
    ) = BackupFile(
        format = format,
        exportedAt = "2026-10-05T03:20:30.123Z",
        app = app,
        categories = categories,
        dishes = dishes,
        images = listOf("images/3b5128dc-fe48-44e5-abba-753cc1a3aaa9.jpg")
    )

    @Test
    fun encodeDecodeRoundTripKeepsIds() {
        val decoded = BackupCodec.decode(BackupCodec.encode(backup()))
        assertEquals(BackupCodec.FORMAT, decoded.format)
        assertEquals(BackupCodec.APP_NAME, decoded.app)
        assertEquals(1L, decoded.categories.first().id)
        assertEquals(10L, decoded.dishes.first().id)
        assertEquals("images/3b5128dc-fe48-44e5-abba-753cc1a3aaa9.jpg", decoded.dishes.first().imagePath)
        assertEquals(DishHighlight.Recommended, decoded.dishes.first().highlight)
    }

    @Test
    fun encodedJsonUsesSpecFieldNames() {
        val text = BackupCodec.encode(backup())
        listOf("format", "exportedAt", "app", "categories", "dishes", "images").forEach { key ->
            assertTrue("missing key " + key, text.contains(quote + key + quote))
        }
    }

    @Test
    fun validateAcceptsCurrentFormat() {
        BackupCodec.validate(backup())
    }

    @Test
    fun decodeFormat1WithoutHighlightDefaultsToNull() {
        val json = """{
            "format":1,
            "exportedAt":"2026-10-05T03:20:30.123Z",
            "app":"TanPaiMenu",
            "categories":[{"id":1,"name":"热菜","sortOrder":0,"enabled":true,"updatedAt":1700000000000}],
            "dishes":[{"id":10,"categoryId":1,"name":"烤串","priceCents":300,"unit":"串","imagePath":null,"stockEnabled":true,"stockCount":18,"soldOut":false,"description":"","sortOrder":0,"enabled":true,"updatedAt":1700000000000}],
            "images":[]
        }""".trimIndent()

        val decoded = BackupCodec.decode(json)

        assertEquals(1, decoded.format)
        assertNull(decoded.dishes.single().highlight)
        BackupCodec.validate(decoded)
    }

    @Test
    fun validateRejectsNewerFormat() {
        assertThrows(BackupException.IncompatibleFormat::class.java) {
            BackupCodec.validate(backup(format = BackupCodec.FORMAT + 1))
        }
    }

    @Test
    fun validateRejectsForeignApp() {
        assertThrows(BackupException.Corrupt::class.java) {
            BackupCodec.validate(backup(app = "OtherApp"))
        }
    }

    @Test
    fun validateRejectsDishWithMissingCategory() {
        assertThrows(BackupException.Corrupt::class.java) {
            BackupCodec.validate(backup(categories = emptyList()))
        }
    }

    @Test
    fun decodeRejectsMissingFields() {
        assertThrows(SerializationException::class.java) {
            BackupCodec.decode(partialJson())
        }
    }

    @Test
    fun decodeRejectsBrokenJson() {
        assertThrows(SerializationException::class.java) {
            BackupCodec.decode("not json at all")
        }
    }

    @Test
    fun exportedAtIsIsoUtc() {
        val stamp = BackupCodec.nowIsoUtc { 1_700_000_000_000L }
        assertEquals("2023-11-14T22:13:20.000Z", stamp)
    }

    private fun partialJson(): String = String.format(
        "{%cformat%c:1,%capp%c:%cTanPaiMenu%c}",
        quote, quote, quote, quote, quote, quote
    )
}
