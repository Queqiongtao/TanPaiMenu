package com.tanpai.menu.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun observeEnabled(): Flow<List<Category>>

    @Query("SELECT * FROM category ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Query("UPDATE category SET enabled = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("DELETE FROM category")
    suspend fun deleteAll()
}
