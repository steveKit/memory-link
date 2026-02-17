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
 * Tests cover:
 * - Empty events list
 * - All-day events only (7-day lookahead)
 * - Timed events only (14-day lookahead)
 * - Mixed all-day and timed events (returned separately)
 * - Edge cases around date boundaries
 */
class GetNextEventUseCaseTest {

        private lateinit var useCase: GetNextEventUseCase

        @Before
        fun setup() {
                useCase = GetNextEventUseCase()
        }

        // region Empty/No Events

        @Test
        fun `returns empty result when events list is empty`() {
                val now = LocalDateTime.of(2026, 2, 11, 10, 0)
                val result = useCase(now, emptyList())
                assertNull(result.allDayEvent)
                assertNull(result.timedEvent)
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
                assertNull(result.allDayEvent)
                assertEquals("Doctor Appointment", result.timedEvent?.title)
        }

        @Test
        fun `returns earliest timed event when multiple exist`() {
                val now = LocalDateTime.of(2026, 2, 11, 8, 0)
                val events =
                        listOf(
                                createTimedEvent(
                                        "Late Event",
                                        LocalDateTime.of(2026, 2, 11, 15, 0)
                                ),
                                createTimedEvent(
                                        "Early Event",
                                        LocalDateTime.of(2026, 2, 11, 10, 0)
                                ),
                                createTimedEvent(
                                        "Middle Event",
                                        LocalDateTime.of(2026, 2, 11, 12, 0)
                                )
                        )
                val result = useCase(now, events)
                assertEquals("Early Event", result.timedEvent?.title)
        }

        @Test
        fun `skips past timed events`() {
                val now = LocalDateTime.of(2026, 2, 11, 11, 0)
                val events =
                        listOf(
                                createTimedEvent("Past Event", LocalDateTime.of(2026, 2, 11, 9, 0)),
                                createTimedEvent(
                                        "Future Event",
                                        LocalDateTime.of(2026, 2, 11, 14, 0)
                                )
                        )
                val result = useCase(now, events)
                assertEquals("Future Event", result.timedEvent?.title)
        }

        @Test
        fun `returns null timed event when all have passed`() {
                val now = LocalDateTime.of(2026, 2, 11, 18, 0)
                val events =
                        listOf(
                                createTimedEvent(
                                        "Morning Event",
                                        LocalDateTime.of(2026, 2, 11, 9, 0)
                                ),
                                createTimedEvent(
                                        "Afternoon Event",
                                        LocalDateTime.of(2026, 2, 11, 14, 0)
                                )
                        )
                val result = useCase(now, events)
                assertNull(result.timedEvent)
        }

        @Test
        fun `returns timed event from future day within 2-week window`() {
                val now = LocalDateTime.of(2026, 2, 11, 18, 0)
                val events =
                        listOf(
                                createTimedEvent(
                                        "Tomorrow Event",
                                        LocalDateTime.of(2026, 2, 12, 10, 0)
                                )
                        )
                val result = useCase(now, events)
                assertEquals("Tomorrow Event", result.timedEvent?.title)
        }

        @Test
        fun `excludes timed event beyond 2-week window`() {
                val now = LocalDateTime.of(2026, 2, 11, 10, 0)
                val events =
                        listOf(
                                createTimedEvent(
                                        "Far Future Event",
                                        LocalDateTime.of(2026, 2, 26, 10, 0)
                                )
                        )
                val result = useCase(now, events)
                assertNull(result.timedEvent)
        }

        // endregion

        // region All-Day Events Only

        @Test
        fun `returns all-day event when no timed events`() {
                val now = LocalDateTime.of(2026, 2, 11, 10, 0)
                val events =
                        listOf(
                                createAllDayEvent(
                                        "Birthday Party",
                                        LocalDateTime.of(2026, 2, 11, 0, 0)
                                )
                        )
                val result = useCase(now, events)
                assertEquals("Birthday Party", result.allDayEvent?.title)
                assertNull(result.timedEvent)
        }

        @Test
        fun `returns first all-day event when multiple exist on same day`() {
                val now = LocalDateTime.of(2026, 2, 11, 10, 0)
                val events =
                        listOf(
                                createAllDayEvent(
                                        "First All Day",
                                        LocalDateTime.of(2026, 2, 11, 0, 0)
                                ),
                                createAllDayEvent(
                                        "Second All Day",
                                        LocalDateTime.of(2026, 2, 11, 0, 0)
                                )
                        )
                val result = useCase(now, events)
                assertEquals("First All Day", result.allDayEvent?.title)
        }

        @Test
        fun `returns all-day event from future day within 7-day window`() {
                val now = LocalDateTime.of(2026, 2, 11, 10, 0)
                val events =
                        listOf(
                                createAllDayEvent(
                                        "Future All Day",
                                        LocalDateTime.of(2026, 2, 15, 0, 0)
                                )
                        )
                val result = useCase(now, events)
                assertEquals("Future All Day", result.allDayEvent?.title)
        }

        @Test
        fun `excludes all-day event beyond 7-day window`() {
                val now = LocalDateTime.of(2026, 2, 11, 10, 0)
                val events =
                        listOf(
                                createAllDayEvent(
                                        "Far Future All Day",
                                        LocalDateTime.of(2026, 2, 20, 0, 0)
                                )
                        )
                val result = useCase(now, events)
                assertNull(result.allDayEvent)
        }

        @Test
        fun `all-day event today is valid regardless of current time`() {
                // Even at 23:59, today's all-day event should still be returned
                val now = LocalDateTime.of(2026, 2, 11, 23, 59)
                val events =
                        listOf(
                                createAllDayEvent(
                                        "Today All Day",
                                        LocalDateTime.of(2026, 2, 11, 0, 0)
                                )
                        )
                val result = useCase(now, events)
                assertEquals("Today All Day", result.allDayEvent?.title)
        }

        // endregion

        // region Mixed All-Day and Timed Events

        @Test
        fun `returns both all-day and timed event when both exist`() {
                val now = LocalDateTime.of(2026, 2, 11, 8, 0)
                val events =
                        listOf(
                                createAllDayEvent(
                                        "Birthday Party",
                                        LocalDateTime.of(2026, 2, 11, 0, 0)
                                ),
                                createTimedEvent(
                                        "Doctor Appointment",
                                        LocalDateTime.of(2026, 2, 11, 10, 30)
                                )
                        )
                val result = useCase(now, events)
                assertEquals("Birthday Party", result.allDayEvent?.title)
                assertEquals("Doctor Appointment", result.timedEvent?.title)
        }

        @Test
        fun `returns all-day today and timed future when timed is tomorrow`() {
                val now = LocalDateTime.of(2026, 2, 11, 20, 0)
                val events =
                        listOf(
                                createAllDayEvent(
                                        "Birthday Party",
                                        LocalDateTime.of(2026, 2, 11, 0, 0)
                                ),
                                createTimedEvent(
                                        "Tomorrow Event",
                                        LocalDateTime.of(2026, 2, 12, 10, 0)
                                )
                        )
                val result = useCase(now, events)
                assertEquals("Birthday Party", result.allDayEvent?.title)
                assertEquals("Tomorrow Event", result.timedEvent?.title)
        }

        @Test
        fun `returns all-day future and timed future on different days`() {
                // All-day on Friday, Timed on Thursday
                val now = LocalDateTime.of(2026, 2, 11, 10, 0) // Wednesday
                val events =
                        listOf(
                                createAllDayEvent(
                                        "Holiday",
                                        LocalDateTime.of(2026, 2, 13, 0, 0)
                                ), // Friday
                                createTimedEvent(
                                        "Doctor",
                                        LocalDateTime.of(2026, 2, 12, 10, 0)
                                ) // Thursday
                        )
                val result = useCase(now, events)
                assertEquals("Holiday", result.allDayEvent?.title)
                assertEquals("Doctor", result.timedEvent?.title)
        }

        // endregion

        // region Edge Cases

        @Test
        fun `handles event at exactly current time as past`() {
                // Event at exactly now should not be shown (it's already started)
                val now = LocalDateTime.of(2026, 2, 11, 10, 30, 0)
                val events =
                        listOf(
                                createTimedEvent(
                                        "Starting Now",
                                        LocalDateTime.of(2026, 2, 11, 10, 30, 0)
                                ),
                                createTimedEvent(
                                        "Later Event",
                                        LocalDateTime.of(2026, 2, 11, 14, 0, 0)
                                )
                        )
                val result = useCase(now, events)
                assertEquals("Later Event", result.timedEvent?.title)
        }

        @Test
        fun `selects earliest all-day when multiple days have all-day events`() {
                val now = LocalDateTime.of(2026, 2, 11, 10, 0)
                val events =
                        listOf(
                                createAllDayEvent(
                                        "Friday Event",
                                        LocalDateTime.of(2026, 2, 13, 0, 0)
                                ),
                                createAllDayEvent(
                                        "Today Event",
                                        LocalDateTime.of(2026, 2, 11, 0, 0)
                                ),
                                createAllDayEvent(
                                        "Thursday Event",
                                        LocalDateTime.of(2026, 2, 12, 0, 0)
                                )
                        )
                val result = useCase(now, events)
                assertEquals("Today Event", result.allDayEvent?.title)
        }

        @Test
        fun `isToday helper returns correct values`() {
                val today = java.time.LocalDate.of(2026, 2, 11)
                val tomorrow = java.time.LocalDate.of(2026, 2, 12)

                assert(useCase.isToday(today, today))
                assert(!useCase.isToday(tomorrow, today))
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
