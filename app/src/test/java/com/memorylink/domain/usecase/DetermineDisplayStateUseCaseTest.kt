package com.memorylink.domain.usecase

import com.memorylink.domain.model.AppSettings
import com.memorylink.domain.model.CalendarEvent
import com.memorylink.domain.model.DisplayState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DetermineDisplayStateUseCase].
 *
 * Tests cover all state transitions:
 * - SLEEP state (within sleep period)
 * - AWAKE_NO_EVENT state (wake period, no events in lookahead)
 * - AWAKE_WITH_EVENT state (wake period, with events in lookahead)
 * - Boundary conditions at sleep/wake times
 * - Future event date handling (all-day 7 days, timed 2 weeks)
 *
 * Note: DisplayState no longer contains time fields (currentTime, currentDate). The UI reads live
 * system time directly for accurate clock display. These tests verify the logical state transitions
 * only.
 */
class DetermineDisplayStateUseCaseTest {

    private lateinit var useCase: DetermineDisplayStateUseCase
    private lateinit var getNextEventUseCase: GetNextEventUseCase

    @Before
    fun setup() {
        getNextEventUseCase = GetNextEventUseCase()
        useCase = DetermineDisplayStateUseCase(getNextEventUseCase)
    }

    // region Default Settings (sleep: 21:30, wake: 06:00)

    @Test
    fun `returns SLEEP during night hours`() {
        // 23:00 is after sleep (21:30)
        val now = LocalDateTime.of(2026, 2, 11, 23, 0)
        val settings = AppSettings()

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `returns SLEEP during early morning hours`() {
        // 05:00 is before wake (06:00)
        val now = LocalDateTime.of(2026, 2, 11, 5, 0)
        val settings = AppSettings()

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `returns AWAKE_NO_EVENT during day with no events`() {
        // 10:00 is within wake period (06:00 - 21:00)
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings()

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
    }

    @Test
    fun `returns AWAKE_WITH_EVENT during day with timed event today`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings()
        val event =
                CalendarEvent(
                        id = "1",
                        title = "Doctor Appointment",
                        startTime = LocalDateTime.of(2026, 2, 11, 14, 0),
                        endTime = LocalDateTime.of(2026, 2, 11, 15, 0),
                        isAllDay = false
                )

        val result = useCase(now, listOf(event), settings)

        assertTrue(result is DisplayState.AwakeWithEvent)
        val state = result as DisplayState.AwakeWithEvent
        assertEquals("Doctor Appointment", state.timedEventTitle)
        assertEquals(LocalTime.of(14, 0), state.timedEventTime)
        assertNull(state.timedEventDate) // null means today
    }

    // endregion

    // region Sleep/Wake Boundary Tests

    @Test
    fun `returns AWAKE at exactly wake time`() {
        // Exactly at 06:00 should be awake
        val now = LocalDateTime.of(2026, 2, 11, 6, 0)
        val settings = AppSettings()

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
    }

    @Test
    fun `returns SLEEP one minute before wake time`() {
        // 05:59 should still be sleep
        val now = LocalDateTime.of(2026, 2, 11, 5, 59)
        val settings = AppSettings()

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `returns AWAKE one minute before sleep time`() {
        // 20:59 should still be awake
        val now = LocalDateTime.of(2026, 2, 11, 20, 59)
        val settings = AppSettings()

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
    }

    @Test
    fun `returns SLEEP at exactly sleep time`() {
        // Exactly at 21:30 should be sleep (default sleep time)
        val now = LocalDateTime.of(2026, 2, 11, 21, 30)
        val settings = AppSettings()

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
    }

    // endregion

    // region Custom Settings

    @Test
    fun `respects custom wake time`() {
        val now = LocalDateTime.of(2026, 2, 11, 7, 30)
        val settings = AppSettings(wakeTime = LocalTime.of(8, 0), sleepTime = LocalTime.of(21, 0))

        val result = useCase(now, emptyList(), settings)

        // 7:30 is before custom wake time of 8:00
        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `respects custom sleep time`() {
        val now = LocalDateTime.of(2026, 2, 11, 19, 30)
        val settings = AppSettings(wakeTime = LocalTime.of(6, 0), sleepTime = LocalTime.of(19, 0))

        val result = useCase(now, emptyList(), settings)

        // 19:30 is after custom sleep time of 19:00
        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `respects 24-hour format setting`() {
        val now = LocalDateTime.of(2026, 2, 11, 14, 30)
        val settings = AppSettings(use24HourFormat = true)

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
        assertEquals(true, (result as DisplayState.AwakeNoEvent).use24HourFormat)
    }

    @Test
    fun `respects 12-hour format setting`() {
        val now = LocalDateTime.of(2026, 2, 11, 14, 30)
        val settings = AppSettings(use24HourFormat = false)

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
        assertEquals(false, (result as DisplayState.AwakeNoEvent).use24HourFormat)
    }

    // endregion

    // region All-Day Event Display

    @Test
    fun `all-day event today shows with null date`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings(wakeTime = LocalTime.of(7, 0))
        val allDayEvent =
                CalendarEvent(
                        id = "1",
                        title = "Birthday Party",
                        startTime = LocalDateTime.of(2026, 2, 11, 0, 0),
                        endTime = LocalDateTime.of(2026, 2, 12, 0, 0),
                        isAllDay = true
                )

        val result = useCase(now, listOf(allDayEvent), settings)

        assertTrue(result is DisplayState.AwakeWithEvent)
        val state = result as DisplayState.AwakeWithEvent
        assertEquals(1, state.allDayEvents.size)
        assertEquals("Birthday Party", state.allDayEvents[0].title)
        assertNull(state.allDayEvents[0].startDate) // null means today
    }

    @Test
    fun `all-day event future shows with date`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0) // Wednesday
        val settings = AppSettings()
        val allDayEvent =
                CalendarEvent(
                        id = "1",
                        title = "Holiday",
                        startTime = LocalDateTime.of(2026, 2, 13, 0, 0), // Friday
                        endTime = LocalDateTime.of(2026, 2, 14, 0, 0),
                        isAllDay = true
                )

        val result = useCase(now, listOf(allDayEvent), settings)

        assertTrue(result is DisplayState.AwakeWithEvent)
        val state = result as DisplayState.AwakeWithEvent
        assertEquals(1, state.allDayEvents.size)
        assertEquals("Holiday", state.allDayEvents[0].title)
        assertEquals(LocalDate.of(2026, 2, 13), state.allDayEvents[0].startDate)
    }

    // endregion

    // region Timed Event Display - Future Dates

    @Test
    fun `timed event today shows with null date`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings()
        val event =
                CalendarEvent(
                        id = "1",
                        title = "Doctor",
                        startTime = LocalDateTime.of(2026, 2, 11, 14, 0),
                        endTime = LocalDateTime.of(2026, 2, 11, 15, 0),
                        isAllDay = false
                )

        val result = useCase(now, listOf(event), settings)

        assertTrue(result is DisplayState.AwakeWithEvent)
        val state = result as DisplayState.AwakeWithEvent
        assertEquals("Doctor", state.timedEventTitle)
        assertEquals(LocalTime.of(14, 0), state.timedEventTime)
        assertNull(state.timedEventDate) // null means today
    }

    @Test
    fun `timed event future shows with date`() {
        val now = LocalDateTime.of(2026, 2, 11, 18, 0) // No more events today
        val settings = AppSettings()
        val event =
                CalendarEvent(
                        id = "1",
                        title = "Physical Therapy",
                        startTime = LocalDateTime.of(2026, 2, 13, 10, 0), // 2 days later
                        endTime = LocalDateTime.of(2026, 2, 13, 11, 0),
                        isAllDay = false
                )

        val result = useCase(now, listOf(event), settings)

        assertTrue(result is DisplayState.AwakeWithEvent)
        val state = result as DisplayState.AwakeWithEvent
        assertEquals("Physical Therapy", state.timedEventTitle)
        assertEquals(LocalTime.of(10, 0), state.timedEventTime)
        assertEquals(LocalDate.of(2026, 2, 13), state.timedEventDate)
    }

    // endregion

    // region Mixed Events

    @Test
    fun `both all-day and timed events returned when both exist`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings()
        val allDayEvent =
                CalendarEvent(
                        id = "1",
                        title = "Birthday",
                        startTime = LocalDateTime.of(2026, 2, 11, 0, 0),
                        endTime = LocalDateTime.of(2026, 2, 12, 0, 0),
                        isAllDay = true
                )
        val timedEvent =
                CalendarEvent(
                        id = "2",
                        title = "Doctor",
                        startTime = LocalDateTime.of(2026, 2, 11, 14, 0),
                        endTime = LocalDateTime.of(2026, 2, 11, 15, 0),
                        isAllDay = false
                )

        val result = useCase(now, listOf(allDayEvent, timedEvent), settings)

        assertTrue(result is DisplayState.AwakeWithEvent)
        val state = result as DisplayState.AwakeWithEvent
        assertEquals(1, state.allDayEvents.size)
        assertEquals("Birthday", state.allDayEvents[0].title)
        assertNull(state.allDayEvents[0].startDate) // today
        assertEquals("Doctor", state.timedEventTitle)
        assertEquals(LocalTime.of(14, 0), state.timedEventTime)
        assertNull(state.timedEventDate) // today
    }

    @Test
    fun `all-day today with timed future event`() {
        val now = LocalDateTime.of(2026, 2, 11, 20, 0) // Late, no more timed events today
        val settings = AppSettings()
        val allDayEvent =
                CalendarEvent(
                        id = "1",
                        title = "Birthday",
                        startTime = LocalDateTime.of(2026, 2, 11, 0, 0),
                        endTime = LocalDateTime.of(2026, 2, 12, 0, 0),
                        isAllDay = true
                )
        val timedEvent =
                CalendarEvent(
                        id = "2",
                        title = "Tomorrow Event",
                        startTime = LocalDateTime.of(2026, 2, 12, 10, 0),
                        endTime = LocalDateTime.of(2026, 2, 12, 11, 0),
                        isAllDay = false
                )

        val result = useCase(now, listOf(allDayEvent, timedEvent), settings)

        assertTrue(result is DisplayState.AwakeWithEvent)
        val state = result as DisplayState.AwakeWithEvent
        assertEquals(1, state.allDayEvents.size)
        assertEquals("Birthday", state.allDayEvents[0].title)
        assertNull(state.allDayEvents[0].startDate) // today
        assertEquals("Tomorrow Event", state.timedEventTitle)
        assertEquals(LocalDate.of(2026, 2, 12), state.timedEventDate) // tomorrow
    }

    // endregion

    // region Edge Case: Sleep time before wake time (unusual configuration)

    @Test
    fun `handles unusual config where sleep comes before wake`() {
        // Edge case: sleep at 10:00, wake at 18:00 (sleeping during the day)
        val now = LocalDateTime.of(2026, 2, 11, 14, 0)
        val settings = AppSettings(sleepTime = LocalTime.of(10, 0), wakeTime = LocalTime.of(18, 0))

        val result = useCase(now, emptyList(), settings)

        // 14:00 is between sleep (10:00) and wake (18:00), so should be sleeping
        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `handles unusual config awake after wake time`() {
        val now = LocalDateTime.of(2026, 2, 11, 20, 0)
        val settings = AppSettings(sleepTime = LocalTime.of(10, 0), wakeTime = LocalTime.of(18, 0))

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

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
        assertEquals(true, (result as DisplayState.Sleep).use24HourFormat)
    }

    @Test
    fun `awake with event state respects format setting`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings(use24HourFormat = true)
        val event =
                CalendarEvent(
                        id = "1",
                        title = "Event",
                        startTime = LocalDateTime.of(2026, 2, 11, 14, 0),
                        endTime = LocalDateTime.of(2026, 2, 11, 15, 0),
                        isAllDay = false
                )

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

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
    }

    // endregion

    // region Sleep Delay for Events

    @Test
    fun `delays sleep when timed event ends after sleep time`() {
        // Sleep time is 21:00, but event ends at 22:00
        // At 21:30, should still be awake showing the ongoing event
        val now = LocalDateTime.of(2026, 2, 11, 21, 30)
        val settings = AppSettings() // Default sleep: 21:00
        val event =
                CalendarEvent(
                        id = "1",
                        title = "Family Video Call",
                        startTime = LocalDateTime.of(2026, 2, 11, 21, 0),
                        endTime = LocalDateTime.of(2026, 2, 11, 22, 0),
                        isAllDay = false
                )

        val result = useCase(now, listOf(event), settings)

        // Should be awake, not sleep, because event hasn't ended
        assertTrue(result is DisplayState.AwakeNoEvent || result is DisplayState.AwakeWithEvent)
    }

    @Test
    fun `enters sleep after last event ends`() {
        // Event ended at 22:00, now it's 22:30
        val now = LocalDateTime.of(2026, 2, 11, 22, 30)
        val settings = AppSettings() // Default sleep: 21:00
        val event =
                CalendarEvent(
                        id = "1",
                        title = "Family Video Call",
                        startTime = LocalDateTime.of(2026, 2, 11, 21, 0),
                        endTime = LocalDateTime.of(2026, 2, 11, 22, 0),
                        isAllDay = false
                )

        val result = useCase(now, listOf(event), settings)

        // Should be sleep since event has ended
        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `delays sleep for upcoming event after sleep time`() {
        // Sleep time is 21:00, event starts at 22:00
        // At 21:30, should stay awake to show upcoming event
        val now = LocalDateTime.of(2026, 2, 11, 21, 30)
        val settings = AppSettings() // Default sleep: 21:00
        val event =
                CalendarEvent(
                        id = "1",
                        title = "Late Night Show",
                        startTime = LocalDateTime.of(2026, 2, 11, 22, 0),
                        endTime = LocalDateTime.of(2026, 2, 11, 23, 0),
                        isAllDay = false
                )

        val result = useCase(now, listOf(event), settings)

        // Should be awake showing upcoming event
        assertTrue(result is DisplayState.AwakeWithEvent)
        val state = result as DisplayState.AwakeWithEvent
        assertEquals("Late Night Show", state.timedEventTitle)
    }

    @Test
    fun `delays sleep until last event of multiple ends`() {
        // Multiple events after sleep time
        val now = LocalDateTime.of(2026, 2, 11, 22, 30)
        val settings = AppSettings() // Default sleep: 21:00
        val event1 =
                CalendarEvent(
                        id = "1",
                        title = "First Event",
                        startTime = LocalDateTime.of(2026, 2, 11, 21, 0),
                        endTime = LocalDateTime.of(2026, 2, 11, 22, 0), // Already ended
                        isAllDay = false
                )
        val event2 =
                CalendarEvent(
                        id = "2",
                        title = "Second Event",
                        startTime = LocalDateTime.of(2026, 2, 11, 22, 0),
                        endTime = LocalDateTime.of(2026, 2, 11, 23, 30), // Still ongoing
                        isAllDay = false
                )

        val result = useCase(now, listOf(event1, event2), settings)

        // Should still be awake because second event hasn't ended
        assertTrue(result is DisplayState.AwakeNoEvent || result is DisplayState.AwakeWithEvent)
    }

    @Test
    fun `all-day events do not delay sleep`() {
        // Only all-day event, should enter sleep normally
        val now = LocalDateTime.of(2026, 2, 11, 21, 30)
        val settings = AppSettings() // Default sleep: 21:00
        val allDayEvent =
                CalendarEvent(
                        id = "1",
                        title = "Birthday",
                        startTime = LocalDateTime.of(2026, 2, 11, 0, 0),
                        endTime = LocalDateTime.of(2026, 2, 12, 0, 0),
                        isAllDay = true
                )

        val result = useCase(now, listOf(allDayEvent), settings)

        // Should be sleep because all-day events don't delay sleep
        assertTrue(result is DisplayState.Sleep)
    }

    @Test
    fun `timed event from different day does not delay sleep`() {
        // Event is tomorrow, should not affect today's sleep
        val now = LocalDateTime.of(2026, 2, 11, 21, 30)
        val settings = AppSettings() // Default sleep: 21:00
        val event =
                CalendarEvent(
                        id = "1",
                        title = "Tomorrow Event",
                        startTime = LocalDateTime.of(2026, 2, 12, 10, 0),
                        endTime = LocalDateTime.of(2026, 2, 12, 11, 0),
                        isAllDay = false
                )

        val result = useCase(now, listOf(event), settings)

        // Should be sleep because the event is not today
        assertTrue(result is DisplayState.Sleep)
    }

    // endregion

    // region showYearInDate Setting

    @Test
    fun `respects showYearInDate setting true`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings(showYearInDate = true)

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
        assertEquals(true, (result as DisplayState.AwakeNoEvent).showYearInDate)
    }

    @Test
    fun `respects showYearInDate setting false`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings(showYearInDate = false)

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
        assertEquals(false, (result as DisplayState.AwakeNoEvent).showYearInDate)
    }

    @Test
    fun `showYearInDate passed to AwakeWithEvent state`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings(showYearInDate = false)
        val event =
                CalendarEvent(
                        id = "1",
                        title = "Event",
                        startTime = LocalDateTime.of(2026, 2, 13, 14, 0),
                        endTime = LocalDateTime.of(2026, 2, 13, 15, 0),
                        isAllDay = false
                )

        val result = useCase(now, listOf(event), settings)

        assertTrue(result is DisplayState.AwakeWithEvent)
        assertEquals(false, (result as DisplayState.AwakeWithEvent).showYearInDate)
    }

    // endregion

    // region Brightness Setting

    @Test
    fun `sleep state always has 10 percent brightness`() {
        val now = LocalDateTime.of(2026, 2, 11, 23, 0) // Sleep period
        val settings = AppSettings(brightness = 80) // Custom brightness

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.Sleep)
        // Sleep state should use SLEEP_BRIGHTNESS (10) regardless of settings
        assertEquals(DisplayState.SLEEP_BRIGHTNESS, result.brightness)
    }

    @Test
    fun `awake no event state uses configured brightness`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0) // Wake period
        val settings = AppSettings(brightness = 75)

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
        assertEquals(75, result.brightness)
    }

    @Test
    fun `awake with event state uses configured brightness`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0) // Wake period
        val settings = AppSettings(brightness = 60)
        val event =
                CalendarEvent(
                        id = "1",
                        title = "Event",
                        startTime = LocalDateTime.of(2026, 2, 11, 14, 0),
                        endTime = LocalDateTime.of(2026, 2, 11, 15, 0),
                        isAllDay = false
                )

        val result = useCase(now, listOf(event), settings)

        assertTrue(result is DisplayState.AwakeWithEvent)
        assertEquals(60, result.brightness)
    }

    @Test
    fun `awake state defaults to 100 percent brightness`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val settings = AppSettings() // Default brightness is 100

        val result = useCase(now, emptyList(), settings)

        assertTrue(result is DisplayState.AwakeNoEvent)
        assertEquals(DisplayState.DEFAULT_BRIGHTNESS, result.brightness)
    }

    // endregion
}
