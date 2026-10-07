package com.tanpai.menu.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SaleRecordDao {

    /**
     * 某菜品尚未撤销的卖出记录（按 id 倒序）。
     *
     * 不按设备过滤：撤销取「全局最后一条」，两台设备可互相撤销（`docs/design/0020` 第 2.5 节）。
     */
    @Query("SELECT * FROM sale_record WHERE dishId = :dishId AND revoked = 0 ORDER BY id DESC")
    suspend fun getActiveFor(dishId: Long): List<SaleRecord>

    @Query("SELECT COUNT(*) FROM sale_record")
    suspend fun count(): Int

    @Insert
    suspend fun insert(record: SaleRecord): Long

    @Query("UPDATE sale_record SET revoked = 1 WHERE id = :id")
    suspend fun markRevoked(id: Long)

    @Query("DELETE FROM sale_record")
    suspend fun deleteAll()
}
