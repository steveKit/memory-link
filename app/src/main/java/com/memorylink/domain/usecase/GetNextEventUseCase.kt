package com.memorylink.domain.usecase

import com.memorylink.domain.model.CalendarEvent
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Result of finding next events, with separate all-day and timed events.
 *
 * @param allDayEvent The next all-day event within 7 days, or null
 * @param timedEvent The next timed event within 2 weeks, or null
 */
data class NextEventsResult(
        val allDayEvent: CalendarEvent? = null,
        val timedEvent: CalendarEvent? = null
)

/**
 * Use case for determining which events to display next.
 *
 * Returns two separate events:
 * - All-day event: next one within 7 days (displayed in clock area)
 * - Timed event: next one within 2 weeks (displayed in event card)
 *
 * Rules:
 * 1. All-day events are limited to 7 days lookahead
 * 2. Timed events can look ahead up to 2 weeks
 * 3. Both can be returned simultaneously (displayed in different areas)
 * 4. Events that have already started are skipped (for timed events)
 * 5. All-day events for today are included regardless of current time
 */
class GetNextEventUseCase @Inject constructor() {

    companion object {
        /** Maximum days to look ahead for all-day events. */
        const val ALL_DAY_LOOKAHEAD_DAYS = 7L

        /** Maximum days to look ahead for timed events. */
        const val TIMED_LOOKAHEAD_DAYS = 14L
    }

    /**
     * Get the next all-day and timed events to display.
     *
     * @param now Current date/time
     * @param events List of all upcoming calendar events (already filtered to 2-week window)
     * @return NextEventsResult containing both event types (either can be null)
     */
    operator fun invoke(now: LocalDateTime, events: List<CalendarEvent>): NextEventsResult {
        if (events.isEmpty()) return NextEventsResult()

        val today = now.toLocalDate()
        val allDayCutoff = today.plusDays(ALL_DAY_LOOKAHEAD_DAYS)
        val timedCutoff = today.plusDays(TIMED_LOOKAHEAD_DAYS)

        // Find next all-day event within 7 days
        // All-day events for today are valid even if "started" at midnight
        val nextAllDayEvent =
                events
                        .filter { event ->
                            event.isAllDay &&
                                    event.startTime.toLocalDate() >= today &&
                                    event.startTime.toLocalDate() < allDayCutoff
                        }
                        .minByOrNull { it.startTime }

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

        return NextEventsResult(allDayEvent = nextAllDayEvent, timedEvent = nextTimedEvent)
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
