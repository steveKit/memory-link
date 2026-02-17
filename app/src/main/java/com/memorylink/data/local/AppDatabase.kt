package com.memorylink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/** Room database for MemoryLink. Cache retention: 7 days. */
@Database(entities = [EventEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}
