package com.tanpai.menu.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Category::class, Dish::class, SaleRecord::class, AppMeta::class],
    version = 1,
    exportSchema = true
)
abstract class TpDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun dishDao(): DishDao
    abstract fun saleRecordDao(): SaleRecordDao
    abstract fun appMetaDao(): AppMetaDao

    companion object {
        private const val DB_NAME = "tanpai-menu.db"

        fun build(context: Context): TpDatabase =
            Room.databaseBuilder(context, TpDatabase::class.java, DB_NAME).build()
    }
}
