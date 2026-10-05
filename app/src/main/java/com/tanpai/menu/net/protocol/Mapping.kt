package com.tanpai.menu.net.protocol

import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish

/**
 * 数据实体与契约 DTO 的双向映射。
 *
 * 时间在库内为 UTC epoch 毫秒，在契约中为 ISO-8601 UTC 字符串；
 * 解析失败时按 0 处理（仅导入 / 对端的异常数据，正常报文不会出现）。
 */

fun Category.toDto(): CategoryDto = CategoryDto(
    id = id,
    name = name,
    sortOrder = sortOrder,
    enabled = enabled,
    updatedAt = Iso8601.format(updatedAt)
)

fun CategoryDto.toEntity(): Category = Category(
    id = id,
    name = name,
    sortOrder = sortOrder,
    enabled = enabled,
    updatedAt = Iso8601.parseOrNull(updatedAt) ?: 0L
)

fun Dish.toDto(): DishDto = DishDto(
    id = id,
    categoryId = categoryId,
    name = name,
    priceCents = priceCents,
    unit = unit,
    imagePath = imagePath,
    stockEnabled = stockEnabled,
    stockCount = stockCount,
    soldOut = soldOut,
    description = description,
    sortOrder = sortOrder,
    enabled = enabled,
    updatedAt = Iso8601.format(updatedAt)
)

fun DishDto.toEntity(): Dish = Dish(
    id = id,
    categoryId = categoryId,
    name = name,
    priceCents = priceCents,
    unit = unit,
    imagePath = imagePath,
    stockEnabled = stockEnabled,
    stockCount = stockCount,
    soldOut = soldOut,
    description = description,
    sortOrder = sortOrder,
    enabled = enabled,
    updatedAt = Iso8601.parseOrNull(updatedAt) ?: 0L
)