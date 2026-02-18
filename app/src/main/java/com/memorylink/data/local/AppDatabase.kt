package com.memorylink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room database for MemoryLink. Cache retention: 7 days. */
@Database(entities = [EventEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        /**
         * Migration from v1 to v2: Add is_holiday column.
         *
         * Holiday events are cached from an optional secondary calendar and can be
         * toggled on/off in settings. Existing events default to non-holiday (0).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE cached_events ADD COLUMN is_holiday INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
