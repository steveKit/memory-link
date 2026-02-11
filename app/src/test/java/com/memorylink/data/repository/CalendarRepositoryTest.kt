package com.memorylink.data.repository

import app.cash.turbine.test
import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.local.EventDao
import com.memorylink.data.local.EventEntity
import com.memorylink.data.remote.GoogleCalendarService
import com.memorylink.data.remote.GoogleCalendarService.ApiResult
import com.memorylink.data.remote.GoogleCalendarService.CalendarEventDto
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
    // syncEvents tests
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
    fun `syncEvents returns Success and caches events on API success`() = runTest {
        // Given
        val calendarId = "test-calendar-id"
        every { tokenStorage.selectedCalendarId } returns calendarId

        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val startMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli()

        val apiEvents =
                listOf(
                        CalendarEventDto(
                                id = "event-1",
                                title = "Test Event",
                                startTimeMillis = startMillis + 3600000, // 1 hour from midnight
                                endTimeMillis = startMillis + 7200000, // 2 hours from midnight
                                isAllDay = false,
                                isConfigEvent = false
                        )
                )

        coEvery { calendarService.fetchEventsInRange(calendarId, any(), any()) } returns
                ApiResult.Success(apiEvents)

        // When
        val result = repository.syncEvents()

        // Then
        assertTrue(result is CalendarRepository.SyncResult.Success)
        assertEquals(1, (result as CalendarRepository.SyncResult.Success).eventCount)

        // Verify events were cached
        coVerify { eventDao.insertEvents(any()) }

        // Verify old events were evicted
        coVerify { eventDao.deleteOldEvents(any()) }

        // Verify last sync time was updated
        verify { tokenStorage.lastSyncTime = any() }
    }

    @Test
    fun `syncEvents returns NotAuthenticated when API returns not authenticated`() = runTest {
        // Given
        val calendarId = "test-calendar-id"
        every { tokenStorage.selectedCalendarId } returns calendarId

        coEvery { calendarService.fetchEventsInRange(calendarId, any(), any()) } returns
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

        coEvery { calendarService.fetchEventsInRange(calendarId, any(), any()) } returns
                ApiResult.Error("Network error")

        // When
        val result = repository.syncEvents()

        // Then
        assertTrue(result is CalendarRepository.SyncResult.Error)
        assertEquals("Network error", (result as CalendarRepository.SyncResult.Error).message)
    }

    @Test
    fun `syncEvents filters CONFIG events correctly`() = runTest {
        // Given
        val calendarId = "test-calendar-id"
        every { tokenStorage.selectedCalendarId } returns calendarId

        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val startMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli()

        val apiEvents =
                listOf(
                        CalendarEventDto(
                                id = "event-1",
                                title = "Normal Event",
                                startTimeMillis = startMillis,
                                endTimeMillis = startMillis + 3600000,
                                isAllDay = false,
                                isConfigEvent = false
                        ),
                        CalendarEventDto(
                                id = "config-1",
                                title = "[CONFIG] SLEEP 21:00",
                                startTimeMillis = startMillis,
                                endTimeMillis = startMillis + 3600000,
                                isAllDay = false,
                                isConfigEvent = true
                        )
                )

        coEvery { calendarService.fetchEventsInRange(calendarId, any(), any()) } returns
                ApiResult.Success(apiEvents)

        // When
        val result = repository.syncEvents()

        // Then
        assertTrue(result is CalendarRepository.SyncResult.Success)
        assertEquals(2, (result as CalendarRepository.SyncResult.Success).eventCount)

        // Verify both events were cached (config events are cached but filtered from display)
        coVerify {
            eventDao.insertEvents(
                    match { entities ->
                        entities.size == 2 &&
                                entities.any { it.isConfigEvent } &&
                                entities.any { !it.isConfigEvent }
                    }
            )
        }
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
    // Calendar selection tests
    // ============================================================

    @Test
    fun `selectCalendar stores calendar ID`() {
        // Given
        val calendarId = "my-calendar-id"

        // When
        repository.selectCalendar(calendarId)

        // Then
        verify { tokenStorage.selectedCalendarId = calendarId }
    }

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
