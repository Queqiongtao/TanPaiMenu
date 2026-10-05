package com.tanpai.menu.data

import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish

data class SeedCategory(val category: Category, val dishes: List<Dish>)

object SeedMenu {

    val categories: List<SeedCategory> = listOf(
        SeedCategory(
            category = Category(name = "热菜", sortOrder = 1, updatedAt = 0L),
            dishes = listOf(
                Dish(
                    categoryId = 0L, name = "烤串", priceCents = 300, unit = "串",
                    stockEnabled = true, stockCount = 20, sortOrder = 1, updatedAt = 0L
                ),
                Dish(
                    categoryId = 0L, name = "蛋炒饭", priceCents = 1200, unit = "份",
                    stockEnabled = true, stockCount = 10, sortOrder = 2, updatedAt = 0L
                )
            )
        ),
        SeedCategory(
            category = Category(name = "饮品", sortOrder = 2, updatedAt = 0L),
            dishes = listOf(
                Dish(
                    categoryId = 0L, name = "可乐", priceCents = 500, unit = "瓶",
                    stockEnabled = true, stockCount = 30, sortOrder = 1, updatedAt = 0L
                ),
                Dish(
                    categoryId = 0L, name = "矿泉水", priceCents = 200, unit = "瓶",
                    stockEnabled = false, sortOrder = 2, updatedAt = 0L
                )
            )
        )
    )
}
