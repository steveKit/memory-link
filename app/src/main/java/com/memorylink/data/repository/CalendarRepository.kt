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
 * Repository for calendar events.
 *
 * Bridges:
 * - GoogleCalendarService (remote API)
 * - EventDao (local Room cache)
 *
 * Per .clinerules/10-project-meta.md:
 * - Sync Interval: Poll every 5 minutes for new events
 * - Offline Cache: Events are cached locally. Display last-known events if offline.
 * - Cache Retention: Keep 2 weeks of events cached
 *
 * Per .clinerules/20-android.md:
 * - Eviction: Delete events older than 7 days on each sync
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
        data class Success(val eventCount: Int) : SyncResult()
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

    /**
     * Observe today's events (non-config events only). Returns cached events from Room database as
     * a Flow.
     */
    fun observeTodaysEvents(): Flow<List<CalendarEvent>> {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now()
        val dayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        return eventDao.getEventsForDay(dayStart, dayEnd).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /** Observe config events for settings processing. */
    fun observeConfigEvents(): Flow<List<CalendarEvent>> {
        return eventDao.getConfigEvents().map { entities -> entities.map { it.toDomainModel() } }
    }

    /**
     * Sync events from Google Calendar API to local cache.
     *
     * @param forceFullSync If true, fetch 2 weeks of events; otherwise just today
     * @return SyncResult indicating success or failure
     */
    suspend fun syncEvents(forceFullSync: Boolean = false): SyncResult {
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

    /** Select a calendar for syncing. */
    fun selectCalendar(calendarId: String, calendarName: String) {
        tokenStorage.selectedCalendarId = calendarId
        tokenStorage.selectedCalendarName = calendarName
        Log.d(TAG, "Selected calendar: $calendarName ($calendarId)")
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

    /** Clear all cached events. */
    suspend fun clearCache() {
        eventDao.deleteAllEvents()
        Log.d(TAG, "Cache cleared")
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
