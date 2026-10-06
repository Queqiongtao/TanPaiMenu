package com.tanpai.menu.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 库 1 → 2 迁移：新增设备表且不触碰既有菜单数据（`docs/design/0015` 第 2.6 节）。 */
@RunWith(AndroidJUnit4::class)
class TpDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TpDatabase::class.java
    )

    @Test
    fun migrate1To2CreatesDeviceTableAndKeepsMenu() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO category (id, name, sortOrder, enabled, updatedAt) " +
                    "VALUES (1, '热菜', 1, 1, 1000)"
            )
            db.execSQL(
                "INSERT INTO dish (id, categoryId, name, priceCents, unit, imagePath, " +
                    "stockEnabled, stockCount, soldOut, description, sortOrder, enabled, updatedAt) " +
                    "VALUES (1, 1, '烤串', 300, '串', NULL, 1, 5, 0, '', 1, 1, 1000)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, TpDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT name FROM category WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("热菜", cursor.getString(0))
            }
            db.query("SELECT name FROM dish WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("烤串", cursor.getString(0))
            }
            db.execSQL(
                "INSERT INTO connected_device (id, name, role, lastSeen) " +
                    "VALUES ('phone-a', '手机A', 'client', 2000)"
            )
            db.query("SELECT COUNT(*) FROM connected_device").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}