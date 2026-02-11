package com.memorylink.domain.usecase

import com.memorylink.domain.model.CalendarEvent
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GetNextEventUseCase].
 *
 * Tests cover all branches including:
 * - Empty events list
 * - No events for today
 * - All-day events only
 * - Timed events only
 * - Mixed all-day and timed events with 2-hour preview window
 * - Edge cases around the 2-hour boundary
 */
class GetNextEventUseCaseTest {

    private lateinit var useCase: GetNextEventUseCase

    @Before
    fun setup() {
        useCase = GetNextEventUseCase()
    }

    // region Empty/No Events

    @Test
    fun `returns null when events list is empty`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val result = useCase(now, emptyList())
        assertNull(result)
    }

    @Test
    fun `returns null when no events for today`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val events =
                listOf(createTimedEvent("Tomorrow Event", LocalDateTime.of(2026, 2, 12, 14, 0)))
        val result = useCase(now, events)
        assertNull(result)
    }

    // endregion

    // region Timed Events Only

    @Test
    fun `returns next timed event when no all-day events`() {
        val now = LocalDateTime.of(2026, 2, 11, 8, 0)
        val events =
                listOf(
                        createTimedEvent(
                                "Doctor Appointment",
                                LocalDateTime.of(2026, 2, 11, 10, 30)
                        )
                )
        val result = useCase(now, events)
        assertEquals("Doctor Appointment", result?.title)
    }

    @Test
    fun `returns earliest timed event when multiple exist`() {
        val now = LocalDateTime.of(2026, 2, 11, 8, 0)
        val events =
                listOf(
                        createTimedEvent("Late Event", LocalDateTime.of(2026, 2, 11, 15, 0)),
                        createTimedEvent("Early Event", LocalDateTime.of(2026, 2, 11, 10, 0)),
                        createTimedEvent("Middle Event", LocalDateTime.of(2026, 2, 11, 12, 0))
                )
        val result = useCase(now, events)
        assertEquals("Early Event", result?.title)
    }

    @Test
    fun `skips past timed events`() {
        val now = LocalDateTime.of(2026, 2, 11, 11, 0)
        val events =
                listOf(
                        createTimedEvent("Past Event", LocalDateTime.of(2026, 2, 11, 9, 0)),
                        createTimedEvent("Future Event", LocalDateTime.of(2026, 2, 11, 14, 0))
                )
        val result = useCase(now, events)
        assertEquals("Future Event", result?.title)
    }

    @Test
    fun `returns null when all timed events have passed`() {
        val now = LocalDateTime.of(2026, 2, 11, 18, 0)
        val events =
                listOf(
                        createTimedEvent("Morning Event", LocalDateTime.of(2026, 2, 11, 9, 0)),
                        createTimedEvent("Afternoon Event", LocalDateTime.of(2026, 2, 11, 14, 0))
                )
        val result = useCase(now, events)
        assertNull(result)
    }

    // endregion

    // region All-Day Events Only

    @Test
    fun `returns all-day event when no timed events`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val events =
                listOf(createAllDayEvent("Birthday Party", LocalDateTime.of(2026, 2, 11, 0, 0)))
        val result = useCase(now, events)
        assertEquals("Birthday Party", result?.title)
    }

    @Test
    fun `returns first all-day event when multiple exist`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 0)
        val events =
                listOf(
                        createAllDayEvent("First All Day", LocalDateTime.of(2026, 2, 11, 0, 0)),
                        createAllDayEvent("Second All Day", LocalDateTime.of(2026, 2, 11, 0, 0))
                )
        val result = useCase(now, events)
        assertEquals("First All Day", result?.title)
    }

    // endregion

    // region Mixed All-Day and Timed Events - 2-Hour Preview Window

    @Test
    fun `returns all-day event when timed event is more than 2 hours away`() {
        // Now: 8:00, Timed event: 10:30 (2.5 hours away)
        // Preview window starts at 8:30, so all-day should show
        val now = LocalDateTime.of(2026, 2, 11, 8, 0)
        val events =
                listOf(
                        createAllDayEvent("Birthday Party", LocalDateTime.of(2026, 2, 11, 0, 0)),
                        createTimedEvent(
                                "Doctor Appointment",
                                LocalDateTime.of(2026, 2, 11, 10, 30)
                        )
                )
        val result = useCase(now, events)
        assertEquals("Birthday Party", result?.title)
    }

    @Test
    fun `returns timed event when within 2-hour preview window`() {
        // Now: 8:30, Timed event: 10:30 (exactly 2 hours away)
        // Preview window starts at 8:30, so timed event should show
        val now = LocalDateTime.of(2026, 2, 11, 8, 30)
        val events =
                listOf(
                        createAllDayEvent("Birthday Party", LocalDateTime.of(2026, 2, 11, 0, 0)),
                        createTimedEvent(
                                "Doctor Appointment",
                                LocalDateTime.of(2026, 2, 11, 10, 30)
                        )
                )
        val result = useCase(now, events)
        assertEquals("Doctor Appointment", result?.title)
    }

    @Test
    fun `returns timed event when less than 2 hours away`() {
        // Now: 9:00, Timed event: 10:30 (1.5 hours away)
        val now = LocalDateTime.of(2026, 2, 11, 9, 0)
        val events =
                listOf(
                        createAllDayEvent("Birthday Party", LocalDateTime.of(2026, 2, 11, 0, 0)),
                        createTimedEvent(
                                "Doctor Appointment",
                                LocalDateTime.of(2026, 2, 11, 10, 30)
                        )
                )
        val result = useCase(now, events)
        assertEquals("Doctor Appointment", result?.title)
    }

    @Test
    fun `returns to all-day after timed event passes`() {
        // Now: 10:31 (event at 10:30 has passed)
        // No more timed events, should return to all-day
        val now = LocalDateTime.of(2026, 2, 11, 10, 31)
        val events =
                listOf(
                        createAllDayEvent("Birthday Party", LocalDateTime.of(2026, 2, 11, 0, 0)),
                        createTimedEvent(
                                "Doctor Appointment",
                                LocalDateTime.of(2026, 2, 11, 10, 30)
                        )
                )
        val result = useCase(now, events)
        assertEquals("Birthday Party", result?.title)
    }

    @Test
    fun `shows next timed event after first passes when no all-day`() {
        val now = LocalDateTime.of(2026, 2, 11, 10, 31)
        val events =
                listOf(
                        createTimedEvent("Morning Event", LocalDateTime.of(2026, 2, 11, 10, 30)),
                        createTimedEvent("Afternoon Event", LocalDateTime.of(2026, 2, 11, 14, 0))
                )
        val result = useCase(now, events)
        assertEquals("Afternoon Event", result?.title)
    }

    // endregion

    // region Edge Cases

    @Test
    fun `boundary - exactly at preview window start shows timed event`() {
        // Now: 8:30:00, Event: 10:30:00, Preview starts: 8:30:00
        val now = LocalDateTime.of(2026, 2, 11, 8, 30, 0)
        val events =
                listOf(
                        createAllDayEvent("All Day", LocalDateTime.of(2026, 2, 11, 0, 0)),
                        createTimedEvent("Timed", LocalDateTime.of(2026, 2, 11, 10, 30, 0))
                )
        val result = useCase(now, events)
        assertEquals("Timed", result?.title)
    }

    @Test
    fun `boundary - one second before preview window shows all-day`() {
        // Now: 8:29:59, Event: 10:30:00, Preview starts: 8:30:00
        val now = LocalDateTime.of(2026, 2, 11, 8, 29, 59)
        val events =
                listOf(
                        createAllDayEvent("All Day", LocalDateTime.of(2026, 2, 11, 0, 0)),
                        createTimedEvent("Timed", LocalDateTime.of(2026, 2, 11, 10, 30, 0))
                )
        val result = useCase(now, events)
        assertEquals("All Day", result?.title)
    }

    @Test
    fun `handles events exactly at current time`() {
        // Event at exactly now should not be shown (it's already started)
        val now = LocalDateTime.of(2026, 2, 11, 10, 30, 0)
        val events =
                listOf(
                        createTimedEvent("Starting Now", LocalDateTime.of(2026, 2, 11, 10, 30, 0)),
                        createTimedEvent("Later Event", LocalDateTime.of(2026, 2, 11, 14, 0, 0))
                )
        val result = useCase(now, events)
        assertEquals("Later Event", result?.title)
    }

    @Test
    fun `multiple timed events - selects next one within preview window`() {
        // Now: 8:30, Events at 10:30 and 14:00
        // Should show 10:30 event (within preview), not 14:00
        val now = LocalDateTime.of(2026, 2, 11, 8, 30)
        val events =
                listOf(
                        createAllDayEvent("All Day", LocalDateTime.of(2026, 2, 11, 0, 0)),
                        createTimedEvent("First Timed", LocalDateTime.of(2026, 2, 11, 10, 30)),
                        createTimedEvent("Second Timed", LocalDateTime.of(2026, 2, 11, 14, 0))
                )
        val result = useCase(now, events)
        assertEquals("First Timed", result?.title)
    }

    @Test
    fun `timed event without all-day shows even when more than 2 hours away`() {
        // No all-day event, should show timed event regardless of preview window
        val now = LocalDateTime.of(2026, 2, 11, 6, 0)
        val events =
                listOf(createTimedEvent("Far Away Event", LocalDateTime.of(2026, 2, 11, 14, 0)))
        val result = useCase(now, events)
        assertEquals("Far Away Event", result?.title)
    }

    // endregion

    // region Helper Functions

    private fun createTimedEvent(title: String, startTime: LocalDateTime): CalendarEvent {
        return CalendarEvent(
                id = title.hashCode().toString(),
                title = title,
                startTime = startTime,
                endTime = startTime.plusHours(1),
                isAllDay = false
        )
    }

    private fun createAllDayEvent(title: String, dayStart: LocalDateTime): CalendarEvent {
        return CalendarEvent(
                id = title.hashCode().toString(),
                title = title,
                startTime = dayStart,
                endTime = dayStart.plusDays(1),
                isAllDay = true
        )
    }

    // endregion
}
