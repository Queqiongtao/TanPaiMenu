package com.tanpai.menu.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Category::class, Dish::class, SaleRecord::class, AppMeta::class, ConnectedDevice::class],
    version = 3,
    exportSchema = true
)
abstract class TpDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun dishDao(): DishDao
    abstract fun saleRecordDao(): SaleRecordDao
    abstract fun appMetaDao(): AppMetaDao
    abstract fun deviceDao(): DeviceDao

    companion object {
        private const val DB_NAME = "tanpai-menu.db"

        /** 1 → 2：新增已连接设备表，不触碰既有数据（`docs/design/0015` 第 2.6 节）。 */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `connected_device` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, " +
                        "`lastSeen` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        /** 2 → 3：菜品新增可空展示标签，旧数据迁移后保持无标签（`docs/design/0021`）。 */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `dish` ADD COLUMN `highlight` TEXT")
            }
        }

        fun build(context: Context): TpDatabase =
            Room.databaseBuilder(context, TpDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}