package com.memorylink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for cached calendar events.
 *
 * Per .clinerules/40-state-machine.md:
 * - Only show TODAY's events (FR-04)
 * - Advance to next event when current event's start time passes (FR-05)
 */
@Dao
interface EventDao {

    /**
     * Get all events for a specific day. Used to display today's events.
     *
     * @param dayStart Start of day in epoch millis (midnight)
     * @param dayEnd End of day in epoch millis (next midnight)
     * @return Flow of events sorted by start time
     */
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

    /**
     * Get the next upcoming event (not yet started). Used to determine what to display in
     * AWAKE_WITH_EVENT state.
     *
     * @param currentTime Current time in epoch millis
     * @param dayEnd End of day in epoch millis
     * @return Flow of the next event, or null if no upcoming events
     */
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

    /**
     * Get all upcoming events within a time range (for 2-week lookahead). Returns non-config events
     * that haven't started yet.
     *
     * @param currentTime Current time in epoch millis
     * @param endTime End of lookahead window in epoch millis
     * @return Flow of upcoming events sorted by start time
     */
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
     * Get all config events for processing. Config events are parsed for settings (SLEEP, WAKE,
     * BRIGHTNESS, etc.)
     */
    @Query(
            """
        SELECT * FROM cached_events 
        WHERE is_config_event = 1
        ORDER BY start_time DESC
    """
    )
    fun getConfigEvents(): Flow<List<EventEntity>>

    /** Insert or update events (upsert). Uses REPLACE strategy for simplicity. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    /** Insert a single event. */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvent(event: EventEntity)

    /**
     * Delete events older than the specified cutoff. Per .clinerules: Delete events older than 7
     * days on each sync.
     *
     * @param cutoff Cutoff timestamp in epoch millis
     * @return Number of deleted rows
     */
    @Query("DELETE FROM cached_events WHERE fetched_at < :cutoff")
    suspend fun deleteOldEvents(cutoff: Long): Int

    /** Delete all events. Used for testing or full cache clear. */
    @Query("DELETE FROM cached_events") suspend fun deleteAllEvents()

    /**
     * Delete specific events by their IDs. Used for incremental sync when events are deleted from
     * the calendar.
     *
     * @param ids List of event IDs to delete
     * @return Number of deleted rows
     */
    @Query("DELETE FROM cached_events WHERE id IN (:ids)")
    suspend fun deleteEventsByIds(ids: List<String>): Int

    /** Get count of cached events. Useful for debugging/logging. */
    @Query("SELECT COUNT(*) FROM cached_events") suspend fun getEventCount(): Int
}
