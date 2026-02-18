package com.memorylink.domain.usecase

import com.memorylink.domain.model.CalendarEvent
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Result containing all-day events (7-day window) and next timed event (2-week window).
 *
 * All-day events are returned as a list, sorted by:
 * 1. Holiday events first (if from holiday calendar)
 * 2. Then by start date
 */
data class NextEventsResult(
        val allDayEvents: List<CalendarEvent> = emptyList(),
        val timedEvent: CalendarEvent? = null
) {
        /** Convenience: first all-day event, or null if none. */
        val allDayEvent: CalendarEvent?
                get() = allDayEvents.firstOrNull()
}

/**
 * Determines which events to display next.
 *
 * All-day events: 7-day lookahead, shown in clock area. Timed events: 2-week lookahead, shown in
 * event card. Both can be returned simultaneously. Past timed events are skipped; today's all-day
 * events are always valid.
 */
class GetNextEventUseCase @Inject constructor() {

    companion object {
        const val ALL_DAY_LOOKAHEAD_DAYS = 7L
        const val TIMED_LOOKAHEAD_DAYS = 14L
    }

    /**
     * Get all-day events and next timed event from the provided list.
     *
     * @param now Current time
     * @param events List of all events (already filtered by holiday toggle if needed)
     * @return NextEventsResult containing all active all-day events and next timed event
     */
    operator fun invoke(now: LocalDateTime, events: List<CalendarEvent>): NextEventsResult {
        if (events.isEmpty()) return NextEventsResult()

        val today = now.toLocalDate()
        val allDayCutoff = today.plusDays(ALL_DAY_LOOKAHEAD_DAYS)
        val timedCutoff = today.plusDays(TIMED_LOOKAHEAD_DAYS)

        // Find ALL all-day events within 7 days
        // Events are sorted: holidays first, then by start date
        val allDayEvents =
                events
                        .filter { event ->
                            event.isAllDay && isAllDayEventActive(event, today, allDayCutoff)
                        }
                        .sortedWith(
                                compareByDescending<CalendarEvent> { it.isHoliday }
                                        .thenBy { it.startTime }
                        )

        // Find next timed event within 2 weeks
        // Must not have started yet (startTime > now)
        val nextTimedEvent =
                events
                        .filter { event ->
                            !event.isAllDay &&
                                    event.startTime.isAfter(now) &&
                                    event.startTime.toLocalDate() < timedCutoff
                        }
                        .minByOrNull { it.startTime }

        return NextEventsResult(allDayEvents = allDayEvents, timedEvent = nextTimedEvent)
    }

    /**
     * Check if an all-day event is active (should be displayed).
     *
     * An all-day event is active if:
     * 1. It's currently ongoing (started on or before today AND ends after today), OR
     * 2. It starts within the lookahead window
     *
     * For multi-day all-day events, endTime is exclusive (e.g., a Feb 15-17 event has endTime of
     * Feb 18 00:00:00).
     *
     * @param event The all-day event to check
     * @param today Today's date
     * @param cutoff The lookahead cutoff date
     * @return true if the event should be displayed
     */
    private fun isAllDayEventActive(
            event: CalendarEvent,
            today: LocalDate,
            cutoff: LocalDate
    ): Boolean {
        val eventStartDate = event.startTime.toLocalDate()
        val eventEndDate = event.endTime.toLocalDate()

        // Check if event is currently ongoing (multi-day event that started before today)
        // endTime is exclusive, so endDate > today means the event is still active today
        val isOngoing = eventStartDate <= today && eventEndDate > today

        // Check if event starts within the lookahead window
        val startsInWindow = eventStartDate >= today && eventStartDate < cutoff

        return isOngoing || startsInWindow
    }

    /**
     * Check if an event is happening today.
     *
     * @param eventDate The event's date
     * @param today Today's date
     * @return true if event is today
     */
    fun isToday(eventDate: LocalDate, today: LocalDate): Boolean {
        return eventDate == today
    }
}
