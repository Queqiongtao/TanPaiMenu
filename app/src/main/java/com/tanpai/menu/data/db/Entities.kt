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
