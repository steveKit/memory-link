package com.memorylink.data.repository

import app.cash.turbine.test
import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.local.EventDao
import com.memorylink.data.local.EventEntity
import com.memorylink.data.remote.GoogleCalendarService
import com.memorylink.data.remote.GoogleCalendarService.ApiResult
import com.memorylink.data.remote.GoogleCalendarService.CalendarEventDto
import com.memorylink.data.remote.GoogleCalendarService.SyncResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for CalendarRepository.
 *
 * Tests the interaction between:
 * - GoogleCalendarService (mocked API)
 * - EventDao (mocked Room)
 * - TokenStorage (mocked preferences)
 *
 * Covers incremental sync with syncToken:
 * - Full sync when no syncToken
 * - Incremental sync when syncToken exists
 * - Handling deleted events
 * - Handling 410 SyncTokenExpired
 * - Calendar change clears cache and syncToken
 */
class CalendarRepositoryTest {

    private lateinit var calendarService: GoogleCalendarService
    private lateinit var eventDao: EventDao
    private lateinit var tokenStorage: TokenStorage
    private lateinit var repository: CalendarRepository

    @Before
    fun setup() {
        calendarService = mockk()
        eventDao = mockk(relaxed = true)
        tokenStorage = mockk(relaxed = true)
        repository = CalendarRepository(calendarService, eventDao, tokenStorage)
    }

    // ============================================================
    // syncEvents tests - Incremental Sync with SyncToken
    // ============================================================

    @Test
    fun `syncEvents returns NoCalendarSelected when no calendar is selected`() = runTest {
        // Given
        every { tokenStorage.selectedCalendarId } returns null

        // When
        val result = repository.syncEvents()

        // Then
        assertEquals(CalendarRepository.SyncResult.NoCalendarSelected, result)
    }

    @Test
    fun `syncEvents performs full sync when no syncToken exists`() = runTest {
        // Given
        val calendarId = "test-calendar-id"
        every { tokenStorage.selectedCalendarId } returns calendarId
        every { tokenStorage.syncToken } returns null // No sync token = full sync

        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val startMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli()

        val syncResponse =
                SyncResponse(
                        events =
                                listOf(
                                        CalendarEventDto(
                                                id = "event-1",
                                                title = "Test Event",
                                                startTimeMillis = startMillis + 3600000,
                                                endTimeMillis = startMillis + 7200000,
                                                isAllDay = false,
                                                isConfigEvent = false
                                        )
                                ),
                        deletedEventIds = emptyList(),
                        nextSyncToken = "new-sync-token"
                )

        coEvery { calendarService.fetchEventsWithSync(calendarId, null) } returns
                ApiResult.Success(syncResponse)

        // When
        val result = repository.syncEvents()

        // Then
        assertTrue(result is CalendarRepository.SyncResult.Success)
        assertEquals(1, (result as CalendarRepository.SyncResult.Success).eventCount)
        assertEquals(0, result.deletedCount)

        // Verify full sync was called with null syncToken
        coVerify { calendarService.fetchEventsWithSync(calendarId, null) }

        // Verify events were cached
        coVerify { eventDao.insertEvents(any()) }

        // Verify sync token was stored
        verify { tokenStorage.syncToken = "new-sync-token" }

        // Verify old events were evicted (only on full sync)
        coVerify { eventDao.deleteOldEvents(any()) }
    }

    @Test
    fun `syncEvents performs incremental sync when syncToken exists`() = runTest {
        // Given
        val calendarId = "test-calendar-id"
        val existingSyncToken = "existing-sync-token"
        every { tokenStorage.selectedCalendarId } returns calendarId
        every { tokenStorage.syncToken } returns existingSyncToken

        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val startMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli()

        val syncResponse =
                SyncResponse(
                        events =
                                listOf(
                                        CalendarEventDto(
                                                id = "event-2",
                                                title = "New Event",
                                                startTimeMillis = startMillis + 3600000,
                                                endTimeMillis = startMillis + 7200000,
                                                isAllDay = false,
                                                isConfigEvent = false
                                        )
                                ),
                        deletedEventIds = emptyList(),
                        nextSyncToken = "updated-sync-token"
                )

        coEvery { calendarService.fetchEventsWithSync(calendarId, existingSyncToken) } returns
                ApiResult.Success(syncResponse)

        // When
        val result = repository.syncEvents()

        // Then
        assertTrue(result is CalendarRepository.SyncResult.Success)

        // Verify incremental sync was called with existing syncToken
        coVerify { calendarService.fetchEventsWithSync(calendarId, existingSyncToken) }

        // Verify sync token was updated
        verify { tokenStorage.syncToken = "updated-sync-token" }

        // Verify old events were NOT evicted (incremental sync skips eviction)
        coVerify(exactly = 0) { eventDao.deleteOldEvents(any()) }
    }

    @Test
    fun `syncEvents deletes events when API returns deleted IDs`() = runTest {
        // Given
        val calendarId = "test-calendar-id"
        every { tokenStorage.selectedCalendarId } returns calendarId
        every { tokenStorage.syncToken } returns "existing-token"

        val syncResponse =
                SyncResponse(
                        events = emptyList(),
                        deletedEventIds = listOf("deleted-event-1", "deleted-event-2"),
                        nextSyncToken = "new-token"
                )

        coEvery { calendarService.fetchEventsWithSync(calendarId, any()) } returns
                ApiResult.Success(syncResponse)
        coEvery { eventDao.deleteEventsByIds(any()) } returns 2

        // When
        val result = repository.syncEvents()

        // Then
        assertTrue(result is CalendarRepository.SyncResult.Success)
        assertEquals(0, (result as CalendarRepository.SyncResult.Success).eventCount)
        assertEquals(2, result.deletedCount)

        // Verify deleted events were removed from cache
        coVerify { eventDao.deleteEventsByIds(listOf("deleted-event-1", "deleted-event-2")) }
    }

    @Test
    fun `syncEvents handles 410 SyncTokenExpired by clearing cache and retrying`() = runTest {
        // Given
        val calendarId = "test-calendar-id"
        every { tokenStorage.selectedCalendarId } returns calendarId
        every { tokenStorage.syncToken } returnsMany listOf("expired-token", null)

        val syncResponse =
                SyncResponse(
                        events = emptyList(),
                        deletedEventIds = emptyList(),
                        nextSyncToken = "fresh-token"
                )

        // First call with expired token returns 410, second call with null succeeds
        coEvery { calendarService.fetchEventsWithSync(calendarId, "expired-token") } returns
                ApiResult.SyncTokenExpired
        coEvery { calendarService.fetchEventsWithSync(calendarId, null) } returns
                ApiResult.Success(syncResponse)

        // When
        val result = repository.syncEvents()

        // Then
        assertTrue(result is CalendarRepository.SyncResult.Success)

        // Verify cache was cleared
        coVerify { eventDao.deleteAllEvents() }

        // Verify sync token was cleared
        verify { tokenStorage.syncToken = null }

        // Verify retry with full sync
        coVerify { calendarService.fetchEventsWithSync(calendarId, null) }
    }

    @Test
    fun `syncEvents returns NotAuthenticated when API returns not authenticated`() = runTest {
        // Given
        val calendarId = "test-calendar-id"
        every { tokenStorage.selectedCalendarId } returns calendarId
        every { tokenStorage.syncToken } returns null

        coEvery { calendarService.fetchEventsWithSync(calendarId, null) } returns
                ApiResult.NotAuthenticated

        // When
        val result = repository.syncEvents()

        // Then
        assertEquals(CalendarRepository.SyncResult.NotAuthenticated, result)
    }

    @Test
    fun `syncEvents returns Error when API fails`() = runTest {
        // Given
        val calendarId = "test-calendar-id"
        every { tokenStorage.selectedCalendarId } returns calendarId
        every { tokenStorage.syncToken } returns null

        coEvery { calendarService.fetchEventsWithSync(calendarId, null) } returns
                ApiResult.Error("Network error")

        // When
        val result = repository.syncEvents()

        // Then
        assertTrue(result is CalendarRepository.SyncResult.Error)
        assertEquals("Network error", (result as CalendarRepository.SyncResult.Error).message)
    }

    @Test
    fun `syncEvents handles both new and deleted events in same sync`() = runTest {
        // Given
        val calendarId = "test-calendar-id"
        every { tokenStorage.selectedCalendarId } returns calendarId
        every { tokenStorage.syncToken } returns "sync-token"

        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val startMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli()

        val syncResponse =
                SyncResponse(
                        events =
                                listOf(
                                        CalendarEventDto(
                                                id = "new-event",
                                                title = "New Event",
                                                startTimeMillis = startMillis,
                                                endTimeMillis = startMillis + 3600000,
                                                isAllDay = false,
                                                isConfigEvent = false
                                        ),
                                        CalendarEventDto(
                                                id = "updated-event",
                                                title = "Updated Event (renamed)",
                                                startTimeMillis = startMillis + 7200000,
                                                endTimeMillis = startMillis + 10800000,
                                                isAllDay = false,
                                                isConfigEvent = false
                                        )
                                ),
                        deletedEventIds = listOf("removed-event"),
                        nextSyncToken = "new-token"
                )

        coEvery { calendarService.fetchEventsWithSync(calendarId, any()) } returns
                ApiResult.Success(syncResponse)
        coEvery { eventDao.deleteEventsByIds(any()) } returns 1

        // When
        val result = repository.syncEvents()

        // Then
        assertTrue(result is CalendarRepository.SyncResult.Success)
        assertEquals(2, (result as CalendarRepository.SyncResult.Success).eventCount)
        assertEquals(1, result.deletedCount)

        // Verify new events were inserted
        coVerify { eventDao.insertEvents(match { it.size == 2 }) }

        // Verify deleted event was removed
        coVerify { eventDao.deleteEventsByIds(listOf("removed-event")) }
    }

    // ============================================================
    // Calendar selection tests - Cache clearing on change
    // ============================================================

    @Test
    fun `selectCalendar clears cache when calendar changes`() = runTest {
        // Given
        val oldCalendarId = "old-calendar"
        val newCalendarId = "new-calendar"
        val newCalendarName = "New Calendar"
        every { tokenStorage.selectedCalendarId } returns oldCalendarId

        // When
        val changed = repository.selectCalendar(newCalendarId, newCalendarName)

        // Then
        assertTrue(changed)

        // Verify cache was cleared
        coVerify { eventDao.deleteAllEvents() }

        // Verify sync token was cleared
        verify { tokenStorage.syncToken = null }

        // Verify new calendar was stored
        verify { tokenStorage.selectedCalendarId = newCalendarId }
        verify { tokenStorage.selectedCalendarName = newCalendarName }
    }

    @Test
    fun `selectCalendar does not clear cache when same calendar is selected`() = runTest {
        // Given
        val calendarId = "same-calendar"
        val calendarName = "Same Calendar"
        every { tokenStorage.selectedCalendarId } returns calendarId

        // When
        val changed = repository.selectCalendar(calendarId, calendarName)

        // Then
        assertFalse(changed)

        // Verify cache was NOT cleared
        coVerify(exactly = 0) { eventDao.deleteAllEvents() }
    }

    @Test
    fun `selectCalendar clears cache when first calendar is selected`() = runTest {
        // Given - no previous calendar
        every { tokenStorage.selectedCalendarId } returns null

        // When
        val changed = repository.selectCalendar("new-calendar", "New Calendar")

        // Then
        assertTrue(changed)

        // Verify cache was cleared (even though empty)
        coVerify { eventDao.deleteAllEvents() }
    }

    // ============================================================
    // clearCache tests
    // ============================================================

    @Test
    fun `clearCache deletes all events and resets sync token`() = runTest {
        // When
        repository.clearCache()

        // Then
        coVerify { eventDao.deleteAllEvents() }
        verify { tokenStorage.syncToken = null }
    }

    // ============================================================
    // observeTodaysEvents tests
    // ============================================================

    @Test
    fun `observeTodaysEvents returns events from DAO converted to domain models`() = runTest {
        // Given
        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val startMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli()

        val entities =
                listOf(
                        EventEntity(
                                id = "event-1",
                                title = "Test Event",
                                startTime = startMillis + 3600000,
                                endTime = startMillis + 7200000,
                                isConfigEvent = false,
                                isAllDay = false,
                                fetchedAt = System.currentTimeMillis()
                        )
                )

        coEvery { eventDao.getEventsForDay(any(), any()) } returns flowOf(entities)

        // When & Then
        repository.observeTodaysEvents().test {
            val events = awaitItem()
            assertEquals(1, events.size)
            assertEquals("event-1", events[0].id)
            assertEquals("Test Event", events[0].title)
            assertEquals(false, events[0].isAllDay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeTodaysEvents handles all-day events correctly`() = runTest {
        // Given
        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val startMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val entities =
                listOf(
                        EventEntity(
                                id = "all-day-1",
                                title = "Birthday Party",
                                startTime = startMillis,
                                endTime = endMillis,
                                isConfigEvent = false,
                                isAllDay = true,
                                fetchedAt = System.currentTimeMillis()
                        )
                )

        coEvery { eventDao.getEventsForDay(any(), any()) } returns flowOf(entities)

        // When & Then
        repository.observeTodaysEvents().test {
            val events = awaitItem()
            assertEquals(1, events.size)
            assertEquals("Birthday Party", events[0].title)
            assertEquals(true, events[0].isAllDay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ============================================================
    // getSyncStatus tests
    // ============================================================

    @Test
    fun `getSyncStatus returns OFFLINE when never synced`() {
        // Given
        every { tokenStorage.lastSyncTime } returns 0L

        // When
        val status = repository.getSyncStatus()

        // Then
        assertEquals(CalendarRepository.SyncStatus.OFFLINE, status)
    }

    @Test
    fun `getSyncStatus returns OK when synced less than 10 minutes ago`() {
        // Given
        val nineMinutesAgo = System.currentTimeMillis() - (9 * 60 * 1000L)
        every { tokenStorage.lastSyncTime } returns nineMinutesAgo

        // When
        val status = repository.getSyncStatus()

        // Then
        assertEquals(CalendarRepository.SyncStatus.OK, status)
    }

    @Test
    fun `getSyncStatus returns STALE when synced 30 minutes ago`() {
        // Given
        val thirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000L)
        every { tokenStorage.lastSyncTime } returns thirtyMinutesAgo

        // When
        val status = repository.getSyncStatus()

        // Then
        assertEquals(CalendarRepository.SyncStatus.STALE, status)
    }

    @Test
    fun `getSyncStatus returns OFFLINE when synced more than 60 minutes ago`() {
        // Given
        val twoHoursAgo = System.currentTimeMillis() - (2 * 60 * 60 * 1000L)
        every { tokenStorage.lastSyncTime } returns twoHoursAgo

        // When
        val status = repository.getSyncStatus()

        // Then
        assertEquals(CalendarRepository.SyncStatus.OFFLINE, status)
    }

    // ============================================================
    // selectedCalendarId tests
    // ============================================================

    @Test
    fun `selectedCalendarId returns value from token storage`() {
        // Given
        val calendarId = "stored-calendar-id"
        every { tokenStorage.selectedCalendarId } returns calendarId

        // When
        val result = repository.selectedCalendarId

        // Then
        assertEquals(calendarId, result)
    }
}
