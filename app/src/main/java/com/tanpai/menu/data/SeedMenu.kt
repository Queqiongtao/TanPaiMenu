package com.tanpai.menu.data

import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish

data class SeedCategory(val category: Category, val dishes: List<Dish>)

/**
 * 首启与「恢复默认菜单」写入的默认菜单：大金馄饨 12 道。
 *
 * 统一不启用库存（`stockEnabled = false`），日常只做沽清；
 * 价格单位为分，展示层再格式化为 `¥10`（整数省略小数位）。
 */
object SeedMenu {

    private data class SeedDish(val name: String, val priceYuan: Int)

    private val menu: List<SeedDish> = listOf(
        SeedDish("原味鲜肉", 10),
        SeedDish("韭菜馄饨", 10),
        SeedDish("玉米馄饨", 12),
        SeedDish("臭香皮蛋", 12),
        SeedDish("贡菜馄饨", 12),
        SeedDish("香菇马蹄", 12),
        SeedDish("虾仁鲜肉", 12),
        SeedDish("全家福", 20),
        SeedDish("荠菜鲜肉", 12),
        SeedDish("辣白菜鲜肉", 12),
        SeedDish("咸蛋黄鲜肉", 14),
        SeedDish("芝士玉米鲜肉", 14)
    )

    val categories: List<SeedCategory> = listOf(
        SeedCategory(
            category = Category(name = "馄饨", sortOrder = 1, updatedAt = 0L),
            dishes = menu.mapIndexed { index, seed ->
                Dish(
                    categoryId = 0L,
                    name = seed.name,
                    priceCents = seed.priceYuan * 100,
                    unit = "份",
                    stockEnabled = false,
                    sortOrder = index + 1,
                    updatedAt = 0L
                )
            }
        )
    )
}