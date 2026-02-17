package com.memorylink.data.repository

import android.util.Log
import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.local.EventDao
import com.memorylink.data.local.EventEntity
import com.memorylink.data.remote.GoogleCalendarService
import com.memorylink.data.remote.GoogleCalendarService.ApiResult
import com.memorylink.domain.model.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository bridging GoogleCalendarService (remote) and EventDao (local cache).
 *
 * Uses syncToken for incremental sync (410 triggers full resync). Syncs every 5 minutes, caches 2
 * weeks, evicts after 7 days.
 */
@Singleton
class CalendarRepository
@Inject
constructor(
        private val calendarService: GoogleCalendarService,
        private val eventDao: EventDao,
        private val tokenStorage: TokenStorage
) {
    /** Result of sync operation. */
    sealed class SyncResult {
        data class Success(val eventCount: Int, val deletedCount: Int = 0) : SyncResult()
        data class Error(val message: String) : SyncResult()
        data object NotAuthenticated : SyncResult()
        data object NoCalendarSelected : SyncResult()
    }

    /** Sync status for UI indicators. */
    enum class SyncStatus {
        OK, // Last sync < 10 minutes ago
        STALE, // Last sync 10-60 minutes ago
        OFFLINE // Last sync > 60 minutes ago or network error
    }

    /** Observe today's displayable events (excludes [CONFIG] events). */
    fun observeTodaysEvents(): Flow<List<CalendarEvent>> {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now()
        val dayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        return eventDao.getEventsForDay(dayStart, dayEnd).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * Observe upcoming events (2-week window). Uses date boundaries to include all-day events.
     * Filtering for "has not started yet" is done at use-case level since all-day events
     * technically start at midnight but should display all day.
     */
    fun observeUpcomingEvents(): Flow<List<CalendarEvent>> {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now()
        val dayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val twoWeeksLater = today.plusWeeks(2).atStartOfDay(zoneId).toInstant().toEpochMilli()

        return eventDao.getEventsInRange(dayStart, twoWeeksLater).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /** Observe config events for settings processing. */
    fun observeConfigEvents(): Flow<List<CalendarEvent>> {
        return eventDao.getConfigEvents().map { entities -> entities.map { it.toDomainModel() } }
    }

    /**
     * Delete a config event from both Google Calendar and local cache.
     *
     * Called after a config event has been successfully processed. Deletes from Google Calendar API
     * first, then removes from local Room cache.
     *
     * @param eventId The event ID to delete
     * @return true if deletion was successful (or event already deleted), false if API error
     */
    suspend fun deleteConfigEvent(eventId: String): Boolean {
        val calendarId = tokenStorage.selectedCalendarId
        if (calendarId.isNullOrBlank()) {
            Log.w(TAG, "Cannot delete config event: no calendar selected")
            return false
        }

        // Delete from Google Calendar API first
        val result = calendarService.deleteEvent(calendarId, eventId)

        return when (result) {
            is ApiResult.Success -> {
                // API delete successful, now remove from local cache
                eventDao.deleteEventById(eventId)
                Log.d(TAG, "Config event deleted: $eventId")
                true
            }
            is ApiResult.NotAuthenticated -> {
                Log.w(TAG, "Cannot delete config event: not authenticated")
                false
            }
            is ApiResult.SyncTokenExpired -> {
                // Shouldn't happen for delete, but handle anyway
                Log.w(TAG, "Unexpected sync token error during delete")
                false
            }
            is ApiResult.Error -> {
                Log.e(TAG, "Failed to delete config event $eventId: ${result.message}")
                false
            }
        }
    }

    /** Sync events using incremental sync. Handles 410 by clearing token and retrying. */
    suspend fun syncEvents(): SyncResult {
        val calendarId = tokenStorage.selectedCalendarId
        if (calendarId.isNullOrBlank()) {
            Log.w(TAG, "No calendar selected")
            return SyncResult.NoCalendarSelected
        }

        val syncToken = tokenStorage.syncToken
        val isFullSync = syncToken == null
        Log.d(TAG, "Starting ${if (isFullSync) "full" else "incremental"} sync")

        // Fetch events using syncToken
        val result = calendarService.fetchEventsWithSync(calendarId, syncToken)

        return when (result) {
            is ApiResult.Success -> {
                val fetchedAt = System.currentTimeMillis()
                val syncResponse = result.data

                // Convert DTOs to entities
                val entities =
                        syncResponse.events.map { dto ->
                            EventEntity(
                                    id = dto.id,
                                    title = dto.title,
                                    startTime = dto.startTimeMillis,
                                    endTime = dto.endTimeMillis,
                                    isConfigEvent = dto.isConfigEvent,
                                    isAllDay = dto.isAllDay,
                                    fetchedAt = fetchedAt
                            )
                        }

                // Insert/update changed events
                if (entities.isNotEmpty()) {
                    eventDao.insertEvents(entities)
                    Log.d(TAG, "Inserted/updated ${entities.size} events")
                }

                // Delete removed events
                var deletedCount = 0
                if (syncResponse.deletedEventIds.isNotEmpty()) {
                    deletedCount = eventDao.deleteEventsByIds(syncResponse.deletedEventIds)
                    Log.d(TAG, "Deleted $deletedCount events")
                }

                // Store the new sync token for next incremental sync
                tokenStorage.syncToken = syncResponse.nextSyncToken
                Log.d(TAG, "Sync token updated: ${syncResponse.nextSyncToken != null}")

                // Evict old events (older than 7 days) - only on full sync to avoid overhead
                if (isFullSync) {
                    val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                    val evictedCount = eventDao.deleteOldEvents(cutoffTime)
                    if (evictedCount > 0) {
                        Log.d(TAG, "Evicted $evictedCount old events")
                    }
                }

                // Update last sync time
                tokenStorage.lastSyncTime = fetchedAt

                Log.d(TAG, "Sync successful: ${entities.size} events synced, $deletedCount deleted")
                SyncResult.Success(entities.size, deletedCount)
            }
            is ApiResult.SyncTokenExpired -> {
                // 410 Gone - sync token is invalid, must perform full sync
                Log.w(TAG, "Sync token expired, clearing cache and performing full sync")
                tokenStorage.syncToken = null
                clearCache()
                // Retry as full sync
                return syncEvents()
            }
            is ApiResult.NotAuthenticated -> {
                Log.w(TAG, "Sync failed: not authenticated")
                SyncResult.NotAuthenticated
            }
            is ApiResult.Error -> {
                Log.e(TAG, "Sync failed: ${result.message}")
                SyncResult.Error(result.message)
            }
        }
    }

    /** @deprecated Use syncEvents() for incremental sync. */
    @Deprecated("Use syncEvents() for incremental sync", ReplaceWith("syncEvents()"))
    suspend fun syncEventsLegacy(forceFullSync: Boolean = false): SyncResult {
        val calendarId = tokenStorage.selectedCalendarId
        if (calendarId.isNullOrBlank()) {
            Log.w(TAG, "No calendar selected")
            return SyncResult.NoCalendarSelected
        }

        // Determine date range
        val today = LocalDate.now()
        val startDate = if (forceFullSync) today.minusDays(7) else today
        val endDate = if (forceFullSync) today.plusDays(14) else today

        // Fetch events from API
        val result = calendarService.fetchEventsInRange(calendarId, startDate, endDate)

        return when (result) {
            is ApiResult.Success -> {
                val fetchedAt = System.currentTimeMillis()

                // Convert DTOs to entities
                val entities =
                        result.data.map { dto ->
                            EventEntity(
                                    id = dto.id,
                                    title = dto.title,
                                    startTime = dto.startTimeMillis,
                                    endTime = dto.endTimeMillis,
                                    isConfigEvent = dto.isConfigEvent,
                                    isAllDay = dto.isAllDay,
                                    fetchedAt = fetchedAt
                            )
                        }

                // Insert/update events in database
                eventDao.insertEvents(entities)

                // Evict old events (older than 7 days)
                val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                val deletedCount = eventDao.deleteOldEvents(cutoffTime)
                if (deletedCount > 0) {
                    Log.d(TAG, "Evicted $deletedCount old events")
                }

                // Update last sync time
                tokenStorage.lastSyncTime = fetchedAt

                Log.d(TAG, "Sync successful: ${entities.size} events cached")
                SyncResult.Success(entities.size)
            }
            is ApiResult.NotAuthenticated -> {
                Log.w(TAG, "Sync failed: not authenticated")
                SyncResult.NotAuthenticated
            }
            is ApiResult.SyncTokenExpired -> {
                // Should not happen with legacy sync, but handle anyway
                Log.w(TAG, "Unexpected sync token expiration in legacy sync")
                SyncResult.Error("Unexpected sync token error")
            }
            is ApiResult.Error -> {
                Log.e(TAG, "Sync failed: ${result.message}")
                SyncResult.Error(result.message)
            }
        }
    }

    /** Get list of available calendars for selection. */
    suspend fun getAvailableCalendars(): ApiResult<List<GoogleCalendarService.CalendarDto>> {
        return calendarService.getCalendarList()
    }

    /** Select a calendar. Clears cache if calendar changes (forces full resync). */
    suspend fun selectCalendar(calendarId: String, calendarName: String): Boolean {
        val previousCalendarId = tokenStorage.selectedCalendarId
        val calendarChanged = previousCalendarId != calendarId

        if (calendarChanged) {
            Log.d(TAG, "Calendar changed from $previousCalendarId to $calendarId")
            // Clear sync token so next sync is a full sync
            tokenStorage.syncToken = null
            // Clear cached events from old calendar
            clearCache()
        }

        tokenStorage.selectedCalendarId = calendarId
        tokenStorage.selectedCalendarName = calendarName
        Log.d(TAG, "Selected calendar: $calendarName ($calendarId)")

        return calendarChanged
    }

    /** Get the currently selected calendar ID. */
    val selectedCalendarId: String?
        get() = tokenStorage.selectedCalendarId

    /** Get the currently selected calendar name. */
    val selectedCalendarName: String?
        get() = tokenStorage.selectedCalendarName

    /** Get the current sync status based on last sync time. */
    fun getSyncStatus(): SyncStatus {
        val lastSync = tokenStorage.lastSyncTime
        if (lastSync == 0L) return SyncStatus.OFFLINE

        val elapsed = System.currentTimeMillis() - lastSync
        return when {
            elapsed < 10 * 60 * 1000L -> SyncStatus.OK // < 10 minutes
            elapsed < 60 * 60 * 1000L -> SyncStatus.STALE // < 60 minutes
            else -> SyncStatus.OFFLINE // >= 60 minutes
        }
    }

    /** Get the last sync timestamp. */
    val lastSyncTime: Long
        get() = tokenStorage.lastSyncTime

    /** Clear all cached events and reset sync token. */
    suspend fun clearCache() {
        eventDao.deleteAllEvents()
        tokenStorage.syncToken = null
        Log.d(TAG, "Cache cleared and sync token reset")
    }

    /** Get count of cached events (for debugging). */
    suspend fun getCachedEventCount(): Int {
        return eventDao.getEventCount()
    }

    /** Convert EventEntity to CalendarEvent domain model. */
    private fun EventEntity.toDomainModel(): CalendarEvent {
        val zoneId = ZoneId.systemDefault()
        return CalendarEvent(
                id = id,
                title = title,
                startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), zoneId),
                endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), zoneId),
                isAllDay = isAllDay
        )
    }

    companion object {
        private const val TAG = "CalendarRepository"
    }
}
