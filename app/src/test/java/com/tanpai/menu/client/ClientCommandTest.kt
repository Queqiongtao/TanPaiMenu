package com.tanpai.menu.client

import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.data.db.DishHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 手机端写命令到契约请求的字段映射（`docs/design/0020` 第 2.1、2.2 节）。
 *
 * 纯 JVM：只验证「命令 → 请求」的取舍（改名 trim、`imagePath` 空串表示清空、
 * `UpdateDish` 绝不携带 `soldOut` / `enabled`），不触碰网络与主机。
 */
class ClientCommandTest {

    private fun draft(
        name: String = " 韭菜馄饨 ",
        priceCents: Int = 1000,
        imagePath: String? = "images/a.jpg",
        highlight: DishHighlight? = DishHighlight.Recommended
    ) = DishDraft(
        name = name,
        priceCents = priceCents,
        unit = "份",
        imagePath = imagePath,
        stockEnabled = true,
        stockCount = 3,
        highlight = highlight,
        description = "现包",
        sortOrder = 2
    )

    @Test
    fun createCategoryTrimsNameAndKeepsSortOrder() {
        val request = ClientCommand.CreateCategory(" 饮品 ", 3).toRequest("r-1")

        assertEquals("饮品", request.name)
        assertEquals(3, request.sortOrder)
        assertEquals("r-1", request.requestId)
    }

    @Test
    fun updateCategoryTrimsName() {
        val request = ClientCommand.UpdateCategory(5L, " 冷饮 ", 1).toRequest("r-5")

        assertEquals("冷饮", request.name)
        assertEquals(1, request.sortOrder)
        assertEquals("r-5", request.requestId)
    }

    @Test
    fun createDishCarriesEveryDraftField() {
        val request = ClientCommand.CreateDish(categoryId = 7L, draft = draft()).toRequest("r-2")

        assertEquals(7L, request.categoryId)
        assertEquals("韭菜馄饨", request.name)
        assertEquals(1000, request.priceCents)
        assertEquals("份", request.unit)
        assertEquals("images/a.jpg", request.imagePath)
        assertTrue(request.stockEnabled)
        assertEquals(3, request.stockCount)
        assertEquals(DishHighlight.Recommended, request.highlight)
        assertEquals("现包", request.description)
        assertEquals(2, request.sortOrder)
        assertEquals("r-2", request.requestId)
    }

    @Test
    fun updateDishClearsImageWithEmptyPath() {
        val request = ClientCommand.UpdateDish(9L, draft(imagePath = null)).toRequest("r-3")

        assertEquals("", request.imagePath)
    }

    @Test
    fun updateDishClearsHighlightWhenDraftHasNone() {
        val request = ClientCommand.UpdateDish(9L, draft(highlight = null)).toRequest("r-highlight-clear")

        assertEquals("", request.highlight)
    }

    @Test
    fun updateDishKeepsExistingImagePath() {
        val request = ClientCommand.UpdateDish(9L, draft(imagePath = "images/b.jpg")).toRequest("r-4")

        assertEquals("images/b.jpg", request.imagePath)
    }

    @Test
    fun updateDishNeverTouchesSoldOutEnabledOrCategory() {
        val request = ClientCommand.UpdateDish(9L, draft()).toRequest("r-6")

        assertNull(request.soldOut)
        assertNull(request.enabled)
        assertNull(request.categoryId)
    }

    @Test
    fun setSoldOutOnlyChangesSoldOut() {
        val request = ClientCommand.SetSoldOut(9L, soldOut = true).toRequest("r-7")

        assertEquals(true, request.soldOut)
        assertNull(request.name)
        assertNull(request.priceCents)
        assertNull(request.imagePath)
        assertNull(request.highlight)
        assertEquals("r-7", request.requestId)
    }

    @Test
    fun everyGeneratedRequestIdIsUnique() {
        val ids = (1..16).map { newRequestId() }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun toDraftCopiesEditableFieldsAndDropsRuntimeState() {
        val dish = Dish(
            id = 42L,
            categoryId = 2L,
            name = "虾仁鲜肉",
            priceCents = 2000,
            unit = "份",
            imagePath = "images/c.jpg",
            stockEnabled = true,
            stockCount = 6,
            soldOut = true,
            highlight = DishHighlight.New,
            description = "手打",
            sortOrder = 4,
            enabled = false,
            updatedAt = 123L
        )

        val draft = dish.toDraft()

        assertEquals("虾仁鲜肉", draft.name)
        assertEquals(2000, draft.priceCents)
        assertEquals("份", draft.unit)
        assertEquals("images/c.jpg", draft.imagePath)
        assertTrue(draft.stockEnabled)
        assertEquals(6, draft.stockCount)
        assertEquals(DishHighlight.New, draft.highlight)
        assertEquals("手打", draft.description)
        assertEquals(4, draft.sortOrder)
    }
}