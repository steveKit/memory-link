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

    /**
     * Get all events in a date range (inclusive of start boundary). Uses >= for start to include
     * all-day events that start at midnight. Filtering for "not yet started" timed events is done
     * at use-case level.
     */
    @Query(
            """
        SELECT * FROM cached_events 
        WHERE start_time >= :startTime 
        AND start_time < :endTime
        AND is_config_event = 0
        ORDER BY start_time ASC
    """
    )
    fun getEventsInRange(startTime: Long, endTime: Long): Flow<List<EventEntity>>

    /**
     * Get all events active during a date range, including multi-day events that started before the
     * range but haven't ended yet. An event is "active" if it overlaps with the time window:
     * - Event starts before window ends (start_time < endTime)
     * - Event ends after window starts (end_time > startTime)
     *
     * This is essential for multi-day all-day events where the start date has passed.
     */
    @Query(
            """
        SELECT * FROM cached_events 
        WHERE start_time < :endTime 
        AND end_time > :startTime
        AND is_config_event = 0
        ORDER BY start_time ASC
    """
    )
    fun getActiveEventsInRange(startTime: Long, endTime: Long): Flow<List<EventEntity>>

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

    /** Delete a single event by ID (for config event cleanup after processing). */
    @Query("DELETE FROM cached_events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: String): Int

    @Query("SELECT COUNT(*) FROM cached_events") suspend fun getEventCount(): Int
}
