package com.tanpai.menu.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppMetaDao {

    @Query("SELECT * FROM app_meta WHERE key = :key")
    suspend fun get(key: String): AppMeta?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: AppMeta)
}
