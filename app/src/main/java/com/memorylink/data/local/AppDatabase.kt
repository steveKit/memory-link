package com.memorylink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for MemoryLink.
 * 
 * Contains:
 * - cached_events: Calendar events fetched from Google Calendar
 * 
 * Per plan.md:
 * - Cache retention: 7 days
 * - Sync interval: 5 minutes
 */
@Database(
    entities = [EventEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * DAO for cached calendar events.
     */
    abstract fun eventDao(): EventDao
}
