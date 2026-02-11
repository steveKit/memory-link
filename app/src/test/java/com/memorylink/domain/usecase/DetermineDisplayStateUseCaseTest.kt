package com.memorylink.domain.usecase

import com.memorylink.domain.model.AppSettings
import com.memorylink.domain.model.CalendarEvent
import com.memorylink.domain.model.DisplayState
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DetermineDisplayStateUseCase].
 *
 * Tests cover all state transitions:
 * - SLEEP state (within sleep period)
 * - AWAKE_NO_EVENT state (wake period, no events)
 * - AWAKE_WITH_EVENT state (wake period, with events)
 * - Boundary conditions at sleep/wake times
 */
class DetermineDisplayStateUseCaseTest {

    private lateinit var useCase: DetermineDisplayStateUseCase
    private lateinit var mockGetNextEventUseCase: GetNextEventUseCase

    @Before
    fun setup() {
        mockGetNextEventUseCase = mockk()
        useCase = DetermineDisplayStateUseCase(mockGetNextEventUseCase)
    }

    // region Default Settings (sleep: 21:00, wake: 06:00)

    @Test
    fun `returns SLEEP during night hours`() {
        // 23:00 is after sleep (21:00)
        val now = LocalDateTime.of(2026, 2, 11, 23, 0)
        val settings = AppSettings()
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
        assertEquals(LocalTime.of(23, 0), (result as DisplayState.Sleep).currentTime)
    }

    @Test
    fun `returns SLEEP during early morning hours`() {
        // 05:00 is before wake (06:00)
        val now = LocalDateTime.of(2026, 2, 11, 5, 0)
        val settings = AppSettings()
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `returns AWAKE_NO_EVENT during day with no events`() {
        // 10:00 is within wake period (06:00 - 21:00)
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings()
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
        val state = result as DisplayState.AwakeNoEvent
        assertEquals(LocalTime.of(10, 0), state.currentTime)
        assertEquals(now.toLocalDate(), state.currentDate)
    }

    @Test
    fun `returns AWAKE_WITH_EVENT during day with event`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings()
        val event = CalendarEvent(
            id = "1",
            title = "Doctor Appointment",
            startTime = LocalDateTime.of(2026, 2, 11, 14, 0),
            endTime = LocalDateTime.of(2026, 2, 11, 15, 0),
            isAllDay = false
        )
        every { mockGetNextEventUseCase(any(), any()) } returns event

        val result = useCase(now, listOf(event), settings)

        assertTrue(result is DisplayState.AwakeWithEvent)
        val state = result as DisplayState.AwakeWithEvent
        assertEquals("Doctor Appointment", state.nextEventTitle)
        assertEquals(LocalTime.of(14, 0), state.nextEventTime)
    }

    // endregion

    // region Sleep/Wake Boundary Tests

    @Test
    fun `returns AWAKE at exactly wake time`() {
        // Exactly at 06:00 should be awake
        val now = LocalDateTime.of(2026, 2, 11, 6, 0)
        val settings = AppSettings()
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
    }

    @Test
    fun `returns SLEEP one minute before wake time`() {
        // 05:59 should still be sleep
        val now = LocalDateTime.of(2026, 2, 11, 5, 59)
        val settings = AppSettings()
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `returns AWAKE one minute before sleep time`() {
        // 20:59 should still be awake
        val now = LocalDateTime.of(2026, 2, 11, 20, 59)
        val settings = AppSettings()
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
    }

    @Test
    fun `returns SLEEP at exactly sleep time`() {
        // Exactly at 21:00 should be sleep
        val now = LocalDateTime.of(2026, 2, 11, 21, 0)
        val settings = AppSettings()
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
    }

    // endregion

    // region Custom Settings

    @Test
    fun `respects custom wake time`() {
        val now = LocalDateTime.of(2026, 2, 11, 7, 30)
        val settings = AppSettings(
            wakeTime = LocalTime.of(8, 0),
            sleepTime = LocalTime.of(21, 0)
        )
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        // 7:30 is before custom wake time of 8:00
        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `respects custom sleep time`() {
        val now = LocalDateTime.of(2026, 2, 11, 19, 30)
        val settings = AppSettings(
            wakeTime = LocalTime.of(6, 0),
            sleepTime = LocalTime.of(19, 0)
        )
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        // 19:30 is after custom sleep time of 19:00
        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `respects 24-hour format setting`() {
        val now = LocalDateTime.of(2026, 2, 11, 14, 30)
        val settings = AppSettings(use24HourFormat = true)
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
        assertEquals(true, (result as DisplayState.AwakeNoEvent).use24HourFormat)
    }

    @Test
    fun `respects 12-hour format setting`() {
        val now = LocalDateTime.of(2026, 2, 11, 14, 30)
        val settings = AppSettings(use24HourFormat = false)
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
        assertEquals(false, (result as DisplayState.AwakeNoEvent).use24HourFormat)
    }

    // endregion

    // region All-Day Event Display Time

    @Test
    fun `all-day event uses wake time for display`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings(wakeTime = LocalTime.of(7, 0))
        val allDayEvent = CalendarEvent(
            id = "1",
            title = "Birthday Party",
            startTime = LocalDateTime.of(2026, 2, 11, 0, 0),
            endTime = LocalDateTime.of(2026, 2, 12, 0, 0),
            isAllDay = true
        )
        every { mockGetNextEventUseCase(any(), any()) } returns allDayEvent

        val result = useCase(now, listOf(allDayEvent), settings)

        assertTrue(result is DisplayState.AwakeWithEvent)
        val state = result as DisplayState.AwakeWithEvent
        assertEquals("Birthday Party", state.nextEventTitle)
        // All-day events show wake time as their "start"
        assertEquals(LocalTime.of(7, 0), state.nextEventTime)
    }

    // endregion

    // region Edge Case: Sleep time before wake time (unusual configuration)

    @Test
    fun `handles unusual config where sleep comes before wake`() {
        // Edge case: sleep at 10:00, wake at 18:00 (sleeping during the day)
        val now = LocalDateTime.of(2026, 2, 11, 14, 0)
        val settings = AppSettings(
            sleepTime = LocalTime.of(10, 0),
            wakeTime = LocalTime.of(18, 0)
        )
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        // 14:00 is between sleep (10:00) and wake (18:00), so should be sleeping
        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `handles unusual config awake after wake time`() {
        val now = LocalDateTime.of(2026, 2, 11, 20, 0)
        val settings = AppSettings(
            sleepTime = LocalTime.of(10, 0),
            wakeTime = LocalTime.of(18, 0)
        )
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        // 20:00 is after wake (18:00) and before sleep (10:00 next day)
        assertTrue(result is DisplayState.AwakeNoEvent)
    }

    // endregion

    // region Format Propagation to States

    @Test
    fun `sleep state respects 24-hour format`() {
        val now = LocalDateTime.of(2026, 2, 11, 23, 0)
        val settings = AppSettings(use24HourFormat = true)
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
        assertEquals(true, (result as DisplayState.Sleep).use24HourFormat)
    }

    @Test
    fun `awake with event state respects format setting`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings(use24HourFormat = true)
        val event = CalendarEvent(
            id = "1",
            title = "Event",
            startTime = LocalDateTime.of(2026, 2, 11, 14, 0),
            endTime = LocalDateTime.of(2026, 2, 11, 15, 0),
            isAllDay = false
        )
        every { mockGetNextEventUseCase(any(), any()) } returns event

        val result = useCase(now, listOf(event), settings)

        assertTrue(result is DisplayState.AwakeWithEvent)
        assertEquals(true, (result as DisplayState.AwakeWithEvent).use24HourFormat)
    }

    // endregion

    // region Midnight Boundary

    @Test
    fun `handles midnight correctly`() {
        // Exactly at midnight should be sleep (default wake is 06:00)
        val now = LocalDateTime.of(2026, 2, 11, 0, 0)
        val settings = AppSettings()
        every { mockGetNextEventUseCase(any(), any()) } returns null

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
    }

    // endregion
}
