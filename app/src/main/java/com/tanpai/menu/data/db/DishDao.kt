package com.tanpai.menu.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DishDao {

    @Query("SELECT * FROM dish WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun observeEnabled(): Flow<List<Dish>>

    @Query("SELECT * FROM dish WHERE categoryId = :categoryId AND enabled = 1 ORDER BY sortOrder ASC, id ASC")
    suspend fun getEnabledByCategory(categoryId: Long): List<Dish>

    @Query("SELECT * FROM dish WHERE id = :id")
    suspend fun getById(id: Long): Dish?

    @Query("SELECT COUNT(*) FROM dish WHERE categoryId = :categoryId AND enabled = 1")
    suspend fun countEnabledInCategory(categoryId: Long): Int

    @Query("SELECT imagePath FROM dish WHERE imagePath IS NOT NULL")
    suspend fun allImagePaths(): List<String>

    @Query("SELECT * FROM dish ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<Dish>

    @Insert
    suspend fun insert(dish: Dish): Long

    @Update
    suspend fun update(dish: Dish)

    @Query("UPDATE dish SET enabled = 0, updatedAt = :updatedAt WHERE categoryId = :categoryId")
    suspend fun softDeleteByCategory(categoryId: Long, updatedAt: Long)

    @Query("UPDATE dish SET enabled = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("DELETE FROM dish")
    suspend fun deleteAll()
}
