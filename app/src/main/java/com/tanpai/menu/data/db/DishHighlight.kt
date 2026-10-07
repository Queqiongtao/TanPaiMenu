package com.tanpai.menu.data.db

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 展示页菜品标签（`docs/design/0021` 第 2.1 节）。
 *
 * null 表示无标签；一个菜品最多一个标签，避免展示页在有限卡片空间里堆叠多个徽标。
 */
@Serializable
enum class DishHighlight(val wireValue: String) {
    @SerialName("recommended")
    Recommended("recommended"),

    @SerialName("new")
    New("new");

    companion object {
        fun fromWire(value: String): DishHighlight? =
            entries.firstOrNull { it.wireValue == value }
    }
}
