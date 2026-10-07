package com.tanpai.menu.client

import com.tanpai.menu.net.protocol.CategoryCreateRequest
import com.tanpai.menu.net.protocol.CategoryPatchRequest
import com.tanpai.menu.net.protocol.DishCreateRequest
import com.tanpai.menu.net.protocol.DishPatchRequest
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.data.db.DishHighlight
import java.util.UUID

/**
 * 菜品编辑弹窗的输入值（`docs/design/0020` 第 2.1 节）。
 *
 * 与 Room 实体解耦：手机端不落库，弹窗只产出这份草稿，再由命令映射成契约请求。
 * `soldOut` / `enabled` 刻意不在其中：编辑菜品不得覆盖他人刚做的沽清操作。
 */
data class DishDraft(
    val name: String,
    val priceCents: Int,
    val unit: String,
    val imagePath: String?,
    val stockEnabled: Boolean,
    val stockCount: Int,
    val highlight: DishHighlight?,
    val description: String,
    val sortOrder: Int
)

/** 菜品编辑弹窗交回的实体 → 命令草稿；手机端不落库，`id` / `soldOut` / `enabled` 不参与编辑。 */
fun Dish.toDraft(): DishDraft = DishDraft(
    name = name,
    priceCents = priceCents,
    unit = unit,
    imagePath = imagePath,
    stockEnabled = stockEnabled,
    stockCount = stockCount,
    highlight = highlight,
    description = description,
    sortOrder = sortOrder
)

/**
 * 手机端写命令（`docs/design/0020` 第 2.1、2.2 节）。
 *
 * 命令只描述「要做什么」，到 HTTP 请求的映射见本文件下方的 `toRequest` 与
 * `ClientCommandExecutor`；重试与等待广播对齐由 `ClientSession` 负责。
 */
sealed interface ClientCommand {

    data class CreateCategory(val name: String, val sortOrder: Int) : ClientCommand

    data class UpdateCategory(val id: Long, val name: String, val sortOrder: Int) : ClientCommand

    data class DeleteCategory(val id: Long) : ClientCommand

    data class CreateDish(val categoryId: Long, val draft: DishDraft) : ClientCommand

    data class UpdateDish(val id: Long, val draft: DishDraft) : ClientCommand

    data class DeleteDish(val id: Long) : ClientCommand

    data class Sell(val id: Long) : ClientCommand

    data class Unsell(val id: Long) : ClientCommand

    data class SetSoldOut(val id: Long, val soldOut: Boolean) : ClientCommand

    data object ResetMenu : ClientCommand
}

/** 每次请求生成唯一 `requestId`，供主机做幂等回放（重试不会重复落库）。 */
internal fun newRequestId(): String = UUID.randomUUID().toString()

internal fun ClientCommand.CreateCategory.toRequest(requestId: String = newRequestId()) =
    CategoryCreateRequest(name = name.trim(), sortOrder = sortOrder, requestId = requestId)

internal fun ClientCommand.UpdateCategory.toRequest(requestId: String = newRequestId()) =
    CategoryPatchRequest(name = name.trim(), sortOrder = sortOrder, requestId = requestId)

internal fun ClientCommand.CreateDish.toRequest(requestId: String = newRequestId()) =
    DishCreateRequest(
        categoryId = categoryId,
        name = draft.name.trim(),
        priceCents = draft.priceCents,
        unit = draft.unit,
        imagePath = draft.imagePath,
        stockEnabled = draft.stockEnabled,
        stockCount = draft.stockCount,
        highlight = draft.highlight,
        description = draft.description,
        sortOrder = draft.sortOrder,
        requestId = requestId
    )

/**
 * 菜品整体更新。
 *
 * `imagePath` 用空串表示清空（契约里 null 表示不修改），`soldOut` / `enabled` 一律不携带。
 */
internal fun ClientCommand.UpdateDish.toRequest(requestId: String = newRequestId()) =
    DishPatchRequest(
        name = draft.name.trim(),
        priceCents = draft.priceCents,
        unit = draft.unit,
        imagePath = draft.imagePath.orEmpty(),
        stockEnabled = draft.stockEnabled,
        stockCount = draft.stockCount,
        highlight = draft.highlight?.wireValue.orEmpty(),
        description = draft.description,
        sortOrder = draft.sortOrder,
        requestId = requestId
    )

/** 沽清 / 恢复：只改 `soldOut` 一个字段。 */
internal fun ClientCommand.SetSoldOut.toRequest(requestId: String = newRequestId()) =
    DishPatchRequest(soldOut = soldOut, requestId = requestId)
