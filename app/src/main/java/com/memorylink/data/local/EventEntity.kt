package com.memorylink.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a cached calendar event.
 * 
 * Per plan.md FR-10: Cache events 1 week in advance for offline resilience.
 * Per .clinerules: Evict events older than 7 days on each sync.
 */
@Entity(
    tableName = "cached_events",
    indices = [
        Index(value = ["start_time"]),
        Index(value = ["fetched_at"])
    ]
)
data class EventEntity(
    /**
     * Unique event ID from Google Calendar.
     */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /**
     * Event title/summary.
     */
    @ColumnInfo(name = "title")
    val title: String,

    /**
     * Event start time in epoch milliseconds (UTC).
     */
    @ColumnInfo(name = "start_time")
    val startTime: Long,

    /**
     * Event end time in epoch milliseconds (UTC).
     */
    @ColumnInfo(name = "end_time")
    val endTime: Long,

    /**
     * Whether this is a [CONFIG] event (processed but not displayed).
     */
    @ColumnInfo(name = "is_config_event")
    val isConfigEvent: Boolean = false,

    /**
     * Whether this is an all-day event.
     * All-day events should be displayed at wake time.
     */
    @ColumnInfo(name = "is_all_day")
    val isAllDay: Boolean = false,

    /**
     * Timestamp when this event was fetched/updated (epoch millis).
     * Used for cache eviction.
     */
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long
)
