package com.tanpai.menu.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SaleRecordDao {

    @Query("SELECT * FROM sale_record WHERE dishId = :dishId AND deviceId = :deviceId AND revoked = 0 ORDER BY id DESC")
    suspend fun getActiveFor(dishId: Long, deviceId: String): List<SaleRecord>

    @Query("SELECT COUNT(*) FROM sale_record")
    suspend fun count(): Int

    @Insert
    suspend fun insert(record: SaleRecord): Long

    @Query("UPDATE sale_record SET revoked = 1 WHERE id = :id")
    suspend fun markRevoked(id: Long)

    @Query("DELETE FROM sale_record")
    suspend fun deleteAll()
}
