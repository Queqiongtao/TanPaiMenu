package com.tanpai.menu.host

import com.tanpai.menu.data.db.DishHighlight
import com.tanpai.menu.net.protocol.CategoryDto
import com.tanpai.menu.net.protocol.CategoryPatchRequest
import com.tanpai.menu.net.protocol.DeviceDto
import com.tanpai.menu.net.protocol.DishCreateRequest
import com.tanpai.menu.net.protocol.DishDto
import com.tanpai.menu.net.protocol.DishPatchRequest
import com.tanpai.menu.net.protocol.ErrorCode
import com.tanpai.menu.net.protocol.ImageUploadDto
import com.tanpai.menu.net.protocol.PairResponse
import com.tanpai.menu.net.protocol.ProtocolJson
import com.tanpai.menu.net.protocol.StateDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主机请求管线单测（纯 JVM）：鉴权、幂等、校验、错误映射与图片上传约束。
 */
class HostApiTest {

    private val token = "token-abc"

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        bearer: String? = token
    ) = HostRequest(
        method = method,
        path = path,
        headers = if (bearer == null) emptyMap() else mapOf("Authorization" to "Bearer $bearer"),
        body = body
    )

    private fun HostResponse.errorCode(): String =
        Regex("\"code\":\"([^\"]+)\"").find(bodyText)!!.groupValues[1]

    private fun api(backend: FakeBackend = FakeBackend()) = HostApi(backend, tokenProvider = { token })

    @Test
    fun missingTokenIsRejected() = runTest {
        val response = api().handle(request("GET", "/state", bearer = null))
        assertEquals(401, response.status)
        assertEquals(ErrorCode.UNAUTHORIZED, response.errorCode())
    }

    @Test
    fun wrongTokenIsRejected() = runTest {
        val response = api().handle(request("GET", "/state", bearer = "token-other"))
        assertEquals(401, response.status)
    }

    @Test
    fun stateOnlyContainsEnabledEntities() = runTest {
        val response = api().handle(request("GET", "/state"))
        assertEquals(200, response.status)
        val state = ProtocolJson.decodeFromString(StateDto.serializer(), response.bodyText)
        assertEquals(7L, state.version)
        assertEquals(listOf(1L), state.categories.map { it.id })
        assertEquals(listOf(10L), state.dishes.map { it.id })
    }

    @Test
    fun stateCarriesStoreNameAndClientEditsFlag() = runTest {
        val response = api().handle(request("GET", "/state"))

        assertEquals(200, response.status)
        val state = ProtocolJson.decodeFromString(StateDto.serializer(), response.bodyText)
        assertEquals("大金馄饨", state.storeName)
        assertEquals(false, state.clientEditsEnabled)
    }

    @Test
    fun resetMenuReturnsNoContent() = runTest {
        val backend = FakeBackend()

        val response = api(backend).handle(request("POST", "/menu/reset"))

        assertEquals(204, response.status)
        assertEquals(0, response.body.size)
    }

    @Test
    fun repeatedRequestIdReplaysWithoutReExecuting() = runTest {
        val backend = FakeBackend()
        val pipeline = api(backend)
        val body = """{"categoryId":1,"name":"烤肉","priceCents":500,"requestId":"r-1"}"""

        val first = pipeline.handle(request("POST", "/dishes", body))
        val second = pipeline.handle(request("POST", "/dishes", body))

        assertEquals(201, first.status)
        assertEquals(first.bodyText, second.bodyText)
        assertEquals(1, backend.createDishCalls)
    }

    @Test
    fun blankNameIsRejectedBeforeBackend() = runTest {
        val backend = FakeBackend()
        val response = api(backend).handle(
            request("POST", "/dishes", """{"categoryId":1,"name":"  ","priceCents":100}""")
        )
        assertEquals(400, response.status)
        assertEquals(ErrorCode.INVALID_ARGUMENT, response.errorCode())
        assertEquals(0, backend.createDishCalls)
    }

    @Test
    fun oversizePriceIsRejected() = runTest {
        val response = api().handle(
            request("POST", "/dishes", """{"categoryId":1,"name":"烤肉","priceCents":1000000}""")
        )
        assertEquals(400, response.status)
    }

    @Test
    fun patchDishAcceptsRecommendedAndNewHighlight() = runTest {
        val recommended = api().handle(request("PATCH", "/dishes/10", """{"highlight":"recommended"}"""))
        val newTag = api().handle(request("PATCH", "/dishes/10", """{"highlight":"new"}"""))

        assertEquals(200, recommended.status)
        assertTrue(recommended.bodyText.contains("\"highlight\":\"recommended\""))
        assertEquals(200, newTag.status)
        assertTrue(newTag.bodyText.contains("\"highlight\":\"new\""))
    }

    @Test
    fun patchDishRejectsUnknownHighlight() = runTest {
        val response = api().handle(request("PATCH", "/dishes/10", """{"highlight":"hot"}"""))

        assertEquals(400, response.status)
        assertEquals(ErrorCode.INVALID_ARGUMENT, response.errorCode())
    }

    @Test
    fun malformedBodyIsRejected() = runTest {
        val response = api().handle(request("POST", "/categories", "{not-json"))
        assertEquals(400, response.status)
    }

    @Test
    fun unknownCategoryReturnsInvalidArgument() = runTest {
        val response = api().handle(
            request("POST", "/dishes", """{"categoryId":99,"name":"烤肉","priceCents":100}""")
        )
        assertEquals(400, response.status)
    }

    @Test
    fun unknownDishReturnsNotFound() = runTest {
        val response = api().handle(request("PATCH", "/dishes/999", """{"name":"x"}"""))
        assertEquals(404, response.status)
        assertEquals(ErrorCode.NOT_FOUND, response.errorCode())
    }

    @Test
    fun sellConflictMapsTo409() = runTest {
        val response = api().handle(request("POST", "/dishes/10/sell", """{"deviceId":"phone-a"}"""))
        assertEquals(409, response.status)
        assertEquals(ErrorCode.CONFLICT, response.errorCode())
    }

    @Test
    fun sellWithoutDeviceIdIsRejected() = runTest {
        val response = api().handle(request("POST", "/dishes/10/sell", """{"deviceId":" "}"""))
        assertEquals(400, response.status)
    }

    @Test
    fun imageNameMustMatchContractPattern() = runTest {
        val response = api().handle(request("GET", "/images/not-a-uuid.jpg"))
        assertEquals(404, response.status)
    }

    @Test
    fun imageServedWithDetectedContentType() = runTest {
        val name = "11111111-2222-3333-4444-555555555555.jpg"
        val response = api().handle(request("GET", "/images/$name"))
        assertEquals(200, response.status)
        assertEquals("image/jpeg", response.contentType)
        assertTrue(response.body.isNotEmpty())
    }

    @Test
    fun oversizeImageIsRejected() = runTest {
        val response = api().handleImageUpload(token, "image/jpeg", ByteArray(10 * 1024 * 1024 + 1))
        assertEquals(413, response.status)
        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, response.errorCode())
    }

    @Test
    fun unsupportedImageTypeIsRejected() = runTest {
        val response = api().handleImageUpload(token, "image/gif", ByteArray(16))
        assertEquals(400, response.status)
    }

    @Test
    fun imageUploadWithoutTokenIsRejected() = runTest {
        val response = api().handleImageUpload(null, "image/png", byteArrayOf(1, 2, 3))
        assertEquals(401, response.status)
    }

    @Test
    fun imageUploadReturnsPathAndUrl() = runTest {
        val response = api().handleImageUpload(token, "image/png", byteArrayOf(1, 2, 3))
        assertEquals(201, response.status)
        val dto = ProtocolJson.decodeFromString(ImageUploadDto.serializer(), response.bodyText)
        assertTrue(dto.imagePath.startsWith("images/"))
        assertEquals("/api/v1/${dto.imagePath}", dto.url)
    }

    @Test
    fun unknownRouteReturnsNotFound() = runTest {
        val response = api().handle(request("GET", "/nope"))
        assertEquals(404, response.status)
    }

    // ---- POST /pair：一次性配对码换 token（无需 Bearer，docs/design/0019 第 3 节）----

    private fun pairingApi(verdict: PairVerdict) = HostApi(
        backend = FakeBackend(),
        tokenProvider = { token },
        pairing = { _, _ -> verdict }
    )

    private val pairBody =
        """{"code":"123456","deviceId":"phone-a","name":"手机A"}"""

    @Test
    fun pairWithoutBearerSucceeds() = runTest {
        val response = pairingApi(PairVerdict.Ok)
            .handlePair(request("POST", "/pair", pairBody, bearer = null))

        assertEquals(200, response.status)
        val body = ProtocolJson.decodeFromString(PairResponse.serializer(), response.bodyText)
        assertEquals(token, body.token)
        assertEquals(7L, body.version)
    }

    @Test
    fun pairWithWrongCodeIsUnauthorized() = runTest {
        val response = pairingApi(PairVerdict.Invalid)
            .handlePair(request("POST", "/pair", pairBody, bearer = null))

        assertEquals(401, response.status)
        assertEquals(ErrorCode.UNAUTHORIZED, response.errorCode())
    }

    @Test
    fun pairWhileLockedReportsRemainingSeconds() = runTest {
        val response = pairingApi(PairVerdict.Locked(42))
            .handlePair(request("POST", "/pair", pairBody, bearer = null))

        assertEquals(401, response.status)
        assertTrue(response.bodyText.contains("42"))
    }

    @Test
    fun pairWithBadCodeFormatIsInvalidArgument() = runTest {
        val body = """{"code":"12ab","deviceId":"phone-a","name":"手机A"}"""
        val response = pairingApi(PairVerdict.Ok)
            .handlePair(request("POST", "/pair", body, bearer = null))

        assertEquals(400, response.status)
        assertEquals(ErrorCode.INVALID_ARGUMENT, response.errorCode())
    }

    @Test
    fun pairWithBlankDeviceIdIsInvalidArgument() = runTest {
        val body = """{"code":"123456","deviceId":"","name":"手机A"}"""
        val response = pairingApi(PairVerdict.Ok)
            .handlePair(request("POST", "/pair", body, bearer = null))

        assertEquals(400, response.status)
    }

    @Test
    fun pairWithMalformedBodyIsInvalidArgument() = runTest {
        val response = pairingApi(PairVerdict.Ok)
            .handlePair(request("POST", "/pair", "{", bearer = null))

        assertEquals(400, response.status)
    }

    /** 内存替身：只实现管线断言所需的语义，真实写入由 `RepositoryMenuBackend` 覆盖。 */
    private class FakeBackend(
        private val storeName: String? = "大金馄饨",
        private val clientEdits: Boolean = false
    ) : MenuBackend {

        private val categories = mutableListOf(
            CategoryDto(1, "热菜", 1, true, STAMP),
            CategoryDto(2, "已停用", 2, false, STAMP)
        )

        private val dishes = mutableListOf(
            DishDto(10, 1, "烤串", 300, "串", null, true, 5, false, null, "", 1, true, STAMP),
            DishDto(11, 1, "已下架", 100, "份", null, false, 0, false, null, "", 2, false, STAMP)
        )

        var createDishCalls = 0
            private set

        override suspend fun currentVersion(): Long = 7L

        override suspend fun state(): StateDto = StateDto(
            version = 7,
            categories = categories.filter { it.enabled },
            dishes = dishes.filter { it.enabled },
            storeName = storeName,
            clientEditsEnabled = clientEdits
        )

        override suspend fun categories(): List<CategoryDto> = categories.filter { it.enabled }

        override suspend fun createCategory(name: String, sortOrder: Int): CategoryDto {
            val dto = CategoryDto(categories.size + 1L, name, sortOrder, true, STAMP)
            categories += dto
            return dto
        }

        override suspend fun patchCategory(
            id: Long,
            patch: CategoryPatchRequest
        ): WriteResult<CategoryDto> {
            val index = categories.indexOfFirst { it.id == id }
            if (index < 0) return WriteResult.NotFound
            val updated = categories[index].copy(
                name = patch.name ?: categories[index].name,
                sortOrder = patch.sortOrder ?: categories[index].sortOrder,
                enabled = patch.enabled ?: categories[index].enabled
            )
            categories[index] = updated
            return WriteResult.Ok(updated)
        }

        override suspend fun deleteCategory(id: Long): WriteResult<Unit> {
            val index = categories.indexOfFirst { it.id == id }
            if (index < 0) return WriteResult.NotFound
            categories[index] = categories[index].copy(enabled = false)
            return WriteResult.Ok(Unit)
        }

        override suspend fun dishes(): List<DishDto> = dishes.filter { it.enabled }

        override suspend fun createDish(request: DishCreateRequest): WriteResult<DishDto> {
            createDishCalls++
            if (categories.none { it.id == request.categoryId && it.enabled }) {
                return WriteResult.NotFound
            }
            val dto = DishDto(
                id = dishes.size + 100L,
                categoryId = request.categoryId,
                name = request.name,
                priceCents = request.priceCents,
                unit = request.unit,
                imagePath = request.imagePath,
                stockEnabled = request.stockEnabled,
                stockCount = request.stockCount,
                highlight = request.highlight,
                description = request.description,
                sortOrder = request.sortOrder,
                updatedAt = STAMP
            )
            dishes += dto
            return WriteResult.Ok(dto)
        }

        override suspend fun patchDish(id: Long, patch: DishPatchRequest): WriteResult<DishDto> {
            val index = dishes.indexOfFirst { it.id == id }
            if (index < 0) return WriteResult.NotFound
            val highlight = when (val raw = patch.highlight) {
                null -> dishes[index].highlight
                "" -> null
                else -> DishHighlight.fromWire(raw)
            }
            val updated = dishes[index].copy(
                name = patch.name ?: dishes[index].name,
                highlight = highlight
            )
            dishes[index] = updated
            return WriteResult.Ok(updated)
        }

        override suspend fun deleteDish(id: Long): WriteResult<Unit> {
            val index = dishes.indexOfFirst { it.id == id }
            if (index < 0) return WriteResult.NotFound
            dishes[index] = dishes[index].copy(enabled = false)
            return WriteResult.Ok(Unit)
        }

        override suspend fun sell(dishId: Long, deviceId: String): WriteResult<DishDto> =
            if (dishes.any { it.id == dishId }) WriteResult.Conflict else WriteResult.NotFound

        override suspend fun unsell(dishId: Long, deviceId: String): WriteResult<DishDto> =
            if (dishes.any { it.id == dishId }) WriteResult.Conflict else WriteResult.NotFound

        override suspend fun devices(): List<DeviceDto> =
            listOf(DeviceDto("phone-a", "手机A", "client", STAMP))

        override suspend fun saveImage(bytes: ByteArray): String? =
            "images/11111111-1111-1111-1111-111111111111.jpg"

        override suspend fun readImage(name: String): ImagePayload? =
            if (name == "11111111-2222-3333-4444-555555555555.jpg") {
                ImagePayload(byteArrayOf(1, 2, 3), "image/jpeg")
            } else {
                null
            }

        override suspend fun resetMenu() {
            categories.clear()
            categories += CategoryDto(1, "热菜", 1, true, STAMP)
            dishes.clear()
            dishes += DishDto(10, 1, "烤串", 300, "串", null, true, 5, false, null, "", 1, true, STAMP)
        }
    }

    private companion object {
        const val STAMP = "2026-10-06T00:00:00.000Z"
    }
}
