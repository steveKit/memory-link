package com.memorylink.data.remote

import android.util.Log
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.memorylink.data.auth.GoogleAuthManager
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Service for fetching events from Google Calendar API.
 *
 * Per .clinerules/20-android.md:
 * - Error Handling: Exponential backoff on API failures (1s, 2s, 4s, max 5 retries)
 * - Quota: Max 12 calls/hour (5-min interval) = 288/day
 *
 * Per .clinerules/10-project-meta.md:
 * - Event Lookahead: Fetch today's events (midnight to midnight, device timezone)
 * - Cache Retention: Keep 2 weeks of events cached for offline resilience
 */
@Singleton
class GoogleCalendarService @Inject constructor(private val authManager: GoogleAuthManager) {
    /** Result wrapper for API calls. */
    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error(val message: String, val exception: Exception? = null) :
                ApiResult<Nothing>()
        data object NotAuthenticated : ApiResult<Nothing>()
        /** 410 Gone - sync token is invalid, must perform full sync. */
        data object SyncTokenExpired : ApiResult<Nothing>()
    }

    /** DTO representing a calendar event from the API. */
    data class CalendarEventDto(
            val id: String,
            val title: String,
            val startTimeMillis: Long,
            val endTimeMillis: Long,
            val isAllDay: Boolean,
            val isConfigEvent: Boolean
    )

    /** DTO representing a calendar in the user's calendar list. */
    data class CalendarDto(val id: String, val name: String, val isPrimary: Boolean)

    /**
     * Response from incremental sync API call.
     *
     * Contains:
     * - events: New or updated events
     * - deletedEventIds: IDs of events that were deleted from the calendar
     * - nextSyncToken: Token to use for next incremental sync
     */
    data class SyncResponse(
            val events: List<CalendarEventDto>,
            val deletedEventIds: List<String>,
            val nextSyncToken: String?
    )

    /**
     * Get list of calendars available to the user. Used in admin setup to let user select which
     * calendar to sync.
     */
    suspend fun getCalendarList(): ApiResult<List<CalendarDto>> =
            withContext(Dispatchers.IO) {
                val calendarService =
                        getCalendarService() ?: return@withContext ApiResult.NotAuthenticated

                retryWithBackoff {
                    try {
                        val calendarList = calendarService.calendarList().list().execute()

                        val calendars =
                                calendarList.items?.map { entry ->
                                    CalendarDto(
                                            id = entry.id,
                                            name = entry.summary ?: entry.id,
                                            isPrimary = entry.primary == true
                                    )
                                }
                                        ?: emptyList()

                        Log.d(TAG, "Found ${calendars.size} calendars")
                        ApiResult.Success(calendars)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch calendar list", e)
                        ApiResult.Error("Failed to fetch calendars: ${e.message}", e)
                    }
                }
            }

    /**
     * Fetch today's events from the specified calendar.
     *
     * @param calendarId The calendar ID to fetch from (or "primary" for primary calendar)
     * @return List of events for today
     */
    suspend fun fetchTodaysEvents(calendarId: String): ApiResult<List<CalendarEventDto>> {
        val today = LocalDate.now()
        return fetchEventsInRange(calendarId, today, today)
    }

    /**
     * Fetch events in a date range.
     *
     * Per .clinerules: Cache 2 weeks of events for offline resilience.
     *
     * @param calendarId The calendar ID to fetch from
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return List of events in the range
     */
    suspend fun fetchEventsInRange(
            calendarId: String,
            startDate: LocalDate,
            endDate: LocalDate
    ): ApiResult<List<CalendarEventDto>> =
            withContext(Dispatchers.IO) {
                val calendarService =
                        getCalendarService() ?: return@withContext ApiResult.NotAuthenticated

                val zoneId = ZoneId.systemDefault()
                val startDateTime = startDate.atStartOfDay(zoneId).toInstant()
                val endDateTime = endDate.plusDays(1).atStartOfDay(zoneId).toInstant()

                retryWithBackoff {
                    try {
                        val events =
                                calendarService
                                        .events()
                                        .list(calendarId)
                                        .setTimeMin(DateTime(startDateTime.toEpochMilli()))
                                        .setTimeMax(DateTime(endDateTime.toEpochMilli()))
                                        .setOrderBy("startTime")
                                        .setSingleEvents(true) // Expand recurring events
                                        .setMaxResults(100) // Reasonable limit
                                        .execute()

                        val eventDtos =
                                events.items?.mapNotNull { event -> convertToDto(event) }
                                        ?: emptyList()

                        Log.d(TAG, "Fetched ${eventDtos.size} events for $startDate to $endDate")
                        ApiResult.Success(eventDtos)
                    } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                        if (e.statusCode == 401) {
                            Log.w(TAG, "Token expired, attempting refresh")
                            val refreshed = authManager.refreshAccessToken()
                            if (refreshed) {
                                // Retry will happen via backoff
                                throw e
                            } else {
                                ApiResult.NotAuthenticated
                            }
                        } else {
                            Log.e(TAG, "API error: ${e.statusCode}", e)
                            ApiResult.Error("Calendar API error: ${e.message}", e)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch events", e)
                        ApiResult.Error("Failed to fetch events: ${e.message}", e)
                    }
                }
            }

    /**
     * Fetch events using incremental sync with syncToken.
     *
     * Per Google Calendar API docs:
     * - If syncToken is null, performs full sync and returns nextSyncToken
     * - If syncToken is provided, returns only changes since last sync
     * - Returns SyncTokenExpired if token is invalid (410 response)
     * - Deleted events have status = "cancelled"
     *
     * @param calendarId The calendar ID to fetch from
     * @param syncToken Optional sync token from previous sync (null = full sync)
     * @return SyncResponse with events, deletedEventIds, and nextSyncToken
     */
    suspend fun fetchEventsWithSync(
            calendarId: String,
            syncToken: String?
    ): ApiResult<SyncResponse> =
            withContext(Dispatchers.IO) {
                val calendarService =
                        getCalendarService() ?: return@withContext ApiResult.NotAuthenticated

                try {
                    val allEvents = mutableListOf<CalendarEventDto>()
                    val deletedIds = mutableListOf<String>()
                    var pageToken: String? = null
                    var nextSyncToken: String? = null

                    do {
                        val request =
                                calendarService
                                        .events()
                                        .list(calendarId)
                                        .setSingleEvents(true)
                                        .setMaxResults(250) // Max allowed by API
                                        .setShowDeleted(true) // Required to see cancelled events

                        if (syncToken != null) {
                            // Incremental sync - use syncToken
                            request.syncToken = syncToken
                        } else {
                            // Full sync - set date range (2 weeks back, 2 weeks forward)
                            val zoneId = ZoneId.systemDefault()
                            val today = LocalDate.now()
                            val startDateTime = today.minusDays(7).atStartOfDay(zoneId).toInstant()
                            val endDateTime = today.plusDays(14).atStartOfDay(zoneId).toInstant()
                            request.setTimeMin(DateTime(startDateTime.toEpochMilli()))
                            request.setTimeMax(DateTime(endDateTime.toEpochMilli()))
                        }

                        if (pageToken != null) {
                            request.pageToken = pageToken
                        }

                        val response = request.execute()

                        // Process events
                        response.items?.forEach { event ->
                            if (event.status == "cancelled") {
                                // Event was deleted
                                deletedIds.add(event.id)
                                Log.d(TAG, "Event deleted: ${event.id}")
                            } else {
                                // Event was created or updated
                                convertToDto(event)?.let { dto -> allEvents.add(dto) }
                            }
                        }

                        pageToken = response.nextPageToken
                        nextSyncToken = response.nextSyncToken
                    } while (pageToken != null)

                    Log.d(
                            TAG,
                            "Sync complete: ${allEvents.size} events, ${deletedIds.size} deleted"
                    )
                    ApiResult.Success(
                            SyncResponse(
                                    events = allEvents,
                                    deletedEventIds = deletedIds,
                                    nextSyncToken = nextSyncToken
                            )
                    )
                } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                    when (e.statusCode) {
                        401 -> {
                            Log.w(TAG, "Token expired, attempting refresh")
                            val refreshed = authManager.refreshAccessToken()
                            if (refreshed) {
                                // Recursive retry after token refresh
                                return@withContext fetchEventsWithSync(calendarId, syncToken)
                            } else {
                                ApiResult.NotAuthenticated
                            }
                        }
                        410 -> {
                            // Sync token is invalid - must perform full sync
                            Log.w(TAG, "Sync token expired (410 Gone)")
                            ApiResult.SyncTokenExpired
                        }
                        else -> {
                            Log.e(TAG, "API error: ${e.statusCode}", e)
                            ApiResult.Error("Calendar API error: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync events", e)
                    ApiResult.Error("Failed to sync events: ${e.message}", e)
                }
            }

    /** Convert Google Calendar Event to our DTO. */
    private fun convertToDto(event: Event): CalendarEventDto? {
        val title = event.summary ?: return null // Skip events without title

        // Check if this is a [CONFIG] event
        val isConfig = title.trim().startsWith("[CONFIG]", ignoreCase = true)

        // Handle all-day events vs timed events
        val isAllDay = event.start?.date != null

        val startMillis: Long
        val endMillis: Long

        if (isAllDay) {
            // All-day events use date (not dateTime)
            // Date is in YYYY-MM-DD format, represents start of day in local timezone
            val startDate = event.start.date.value
            val endDate = event.end.date.value

            val zoneId = ZoneId.systemDefault()
            startMillis =
                    LocalDate.parse(startDate.toString())
                            .atStartOfDay(zoneId)
                            .toInstant()
                            .toEpochMilli()
            endMillis =
                    LocalDate.parse(endDate.toString())
                            .atStartOfDay(zoneId)
                            .toInstant()
                            .toEpochMilli()
        } else {
            // Timed events use dateTime
            startMillis = event.start?.dateTime?.value ?: return null
            endMillis = event.end?.dateTime?.value ?: startMillis
        }

        return CalendarEventDto(
                id = event.id,
                title = title,
                startTimeMillis = startMillis,
                endTimeMillis = endMillis,
                isAllDay = isAllDay,
                isConfigEvent = isConfig
        )
    }

    /** Create an authenticated Calendar service instance. */
    private suspend fun getCalendarService(): Calendar? {
        val accessToken = authManager.getValidAccessToken() ?: return null

        return Calendar.Builder(
                        NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                        { request -> request.headers.authorization = "Bearer $accessToken" }
                )
                .setApplicationName("MemoryLink")
                .build()
    }

    /**
     * Retry an operation with exponential backoff.
     *
     * Per .clinerules/20-android.md: Exponential backoff on API failures (1s, 2s, 4s, max 5
     * retries).
     */
    private suspend fun <T> retryWithBackoff(
            maxRetries: Int = MAX_RETRIES,
            initialDelayMs: Long = INITIAL_DELAY_MS,
            block: suspend () -> ApiResult<T>
    ): ApiResult<T> {
        var currentDelay = initialDelayMs
        var lastError: ApiResult.Error? = null

        repeat(maxRetries) { attempt ->
            val result = block()
            when (result) {
                is ApiResult.Success -> return result
                is ApiResult.NotAuthenticated -> return result
                is ApiResult.SyncTokenExpired -> return result
                is ApiResult.Error -> {
                    lastError = result
                    if (attempt < maxRetries - 1) {
                        Log.d(TAG, "Retry attempt ${attempt + 1}, waiting ${currentDelay}ms")
                        delay(currentDelay)
                        currentDelay *= 2 // Exponential backoff
                    }
                }
            }
        }

        return lastError ?: ApiResult.Error("Unknown error after $maxRetries retries")
    }

    companion object {
        private const val TAG = "GoogleCalendarService"
        private const val MAX_RETRIES = 5
        private const val INITIAL_DELAY_MS = 1000L
    }
}
