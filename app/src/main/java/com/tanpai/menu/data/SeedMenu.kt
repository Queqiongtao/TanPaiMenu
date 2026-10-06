package com.tanpai.menu.data

import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish

data class SeedCategory(val category: Category, val dishes: List<Dish>)

/**
 * 首启与「恢复默认菜单」写入的默认菜单：大金馄饨 12 道。
 *
 * 统一不启用库存（`stockEnabled = false`），日常只做沽清；
 * 价格单位为分，展示层再格式化为 `¥10`（整数省略小数位）。
 * [SeedDish.imageAsset] 是内置默认图在 `assets/dishes/` 下的文件名，
 * 由仓储在写库前经 `ImageStore.importSeedAsset` 导入并写入 `Dish.imagePath`。
 */
object SeedMenu {

    private data class SeedDish(val name: String, val priceYuan: Int, val imageAsset: String)

    private val menu: List<SeedDish> = listOf(
        SeedDish("原味鲜肉", 10, "pork.jpg"),
        SeedDish("韭菜馄饨", 10, "chives.jpg"),
        SeedDish("玉米馄饨", 12, "corn.jpg"),
        SeedDish("臭香皮蛋", 12, "century-egg.jpg"),
        SeedDish("贡菜馄饨", 12, "gong-cai.jpg"),
        SeedDish("香菇马蹄", 12, "shiitake-water-chestnut.jpg"),
        SeedDish("虾仁鲜肉", 12, "shrimp.jpg"),
        SeedDish("全家福", 20, "wonton-bowl.jpg"),
        SeedDish("荠菜鲜肉", 12, "shepherds-purse.jpg"),
        SeedDish("辣白菜鲜肉", 12, "kimchi.jpg"),
        SeedDish("咸蛋黄鲜肉", 14, "salted-yolk.jpg"),
        SeedDish("芝士玉米鲜肉", 14, "cheese-corn.jpg")
    )

    /** 种子图 asset 名，顺序与 [categories] 中的菜品一致，供测试与导入遍历。 */
    val imageAssets: List<String> = menu.map { it.imageAsset }

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

    /** 菜品名 -> asset 名，供仓储在写库前解析默认图路径。 */
    val imageAssetByName: Map<String, String> = menu.associate { it.name to it.imageAsset }
}