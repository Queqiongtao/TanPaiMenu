package com.tanpai.menu.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeviceDao {

    @Query("SELECT * FROM connected_device ORDER BY lastSeen DESC")
    suspend fun getAll(): List<ConnectedDevice>

    @Query("SELECT * FROM connected_device WHERE id = :id")
    suspend fun getById(id: String): ConnectedDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: ConnectedDevice)

    @Query("DELETE FROM connected_device WHERE id = :id")
    suspend fun delete(id: String)

    /** 清理超过 [threshold] 未活跃的设备记录，返回删除条数。 */
    @Query("DELETE FROM connected_device WHERE lastSeen < :threshold")
    suspend fun deleteStale(threshold: Long): Int
}