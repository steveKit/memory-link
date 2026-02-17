package com.memorylink.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room entity for cached calendar events. Evicted after 7 days. */
@Entity(
        tableName = "cached_events",
        indices = [Index(value = ["start_time"]), Index(value = ["fetched_at"])]
)
data class EventEntity(
        @PrimaryKey @ColumnInfo(name = "id") val id: String,
        @ColumnInfo(name = "title") val title: String,
        /** Epoch millis (UTC). */
        @ColumnInfo(name = "start_time") val startTime: Long,
        /** Epoch millis (UTC). */
        @ColumnInfo(name = "end_time") val endTime: Long,
        /** [CONFIG] events are parsed for settings, never displayed. */
        @ColumnInfo(name = "is_config_event") val isConfigEvent: Boolean = false,
        @ColumnInfo(name = "is_all_day") val isAllDay: Boolean = false,
        /** Used for cache eviction. */
        @ColumnInfo(name = "fetched_at") val fetchedAt: Long
)
