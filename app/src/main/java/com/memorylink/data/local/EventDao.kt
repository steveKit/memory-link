package com.memorylink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Data Access Object for cached calendar events. See: 40-state-machine.md */
@Dao
interface EventDao {

    /** Get all displayable events for a specific day, sorted by start time. */
    @Query(
            """
        SELECT * FROM cached_events 
        WHERE start_time >= :dayStart 
        AND start_time < :dayEnd 
        AND is_config_event = 0
        ORDER BY start_time ASC
    """
    )
    fun getEventsForDay(dayStart: Long, dayEnd: Long): Flow<List<EventEntity>>

    /** Get the next upcoming event (not yet started) before dayEnd. */
    @Query(
            """
        SELECT * FROM cached_events 
        WHERE start_time > :currentTime 
        AND start_time < :dayEnd
        AND is_config_event = 0
        ORDER BY start_time ASC 
        LIMIT 1
    """
    )
    fun getNextEvent(currentTime: Long, dayEnd: Long): Flow<EventEntity?>

    /** Get all upcoming events within a time range (for 2-week lookahead). */
    @Query(
            """
        SELECT * FROM cached_events 
        WHERE start_time > :currentTime 
        AND start_time < :endTime
        AND is_config_event = 0
        ORDER BY start_time ASC
    """
    )
    fun getUpcomingEvents(currentTime: Long, endTime: Long): Flow<List<EventEntity>>

    /** Get all [CONFIG] events for settings parsing. */
    @Query(
            """
        SELECT * FROM cached_events 
        WHERE is_config_event = 1
        ORDER BY start_time DESC
    """
    )
    fun getConfigEvents(): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvent(event: EventEntity)

    /** Delete events older than cutoff. Eviction runs on each sync (7-day retention). */
    @Query("DELETE FROM cached_events WHERE fetched_at < :cutoff")
    suspend fun deleteOldEvents(cutoff: Long): Int

    @Query("DELETE FROM cached_events") suspend fun deleteAllEvents()

    /** Delete specific events by ID (for incremental sync deletions). */
    @Query("DELETE FROM cached_events WHERE id IN (:ids)")
    suspend fun deleteEventsByIds(ids: List<String>): Int

    @Query("SELECT COUNT(*) FROM cached_events") suspend fun getEventCount(): Int
}
