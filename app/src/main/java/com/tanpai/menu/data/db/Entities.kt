package com.tanpai.menu.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "category")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val updatedAt: Long
)

@Entity(
    tableName = "dish",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index("categoryId")]
)
data class Dish(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: Long,
    val name: String,
    val priceCents: Int,
    val unit: String = "份",
    val imagePath: String? = null,
    val stockEnabled: Boolean = false,
    val stockCount: Int = 0,
    val soldOut: Boolean = false,
    /** 展示标签：推荐 / 新品；null 表示无标签（`docs/design/0021`）。 */
    val highlight: DishHighlight? = null,
    val description: String = "",
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val updatedAt: Long
)

@Entity(
    tableName = "sale_record",
    indices = [Index("dishId"), Index("deviceId")]
)
data class SaleRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dishId: Long,
    val qty: Int = 1,
    val createdAt: Long,
    val deviceId: String,
    val revoked: Boolean = false
)

@Entity(tableName = "app_meta")
data class AppMeta(
    @PrimaryKey val key: String,
    val value: String
)

/**
 * 已连接的客户端设备（`docs/design/0015` 第 2.6 节）。
 *
 * `id` 由客户端在 WS 连接参数中提供；[lastSeen] 为最近一次活跃时间（UTC epoch 毫秒），
 * 超过 30s 未更新即视为离线。
 */
@Entity(tableName = "connected_device")
data class ConnectedDevice(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,
    val lastSeen: Long
)
