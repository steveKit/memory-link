package com.memorylink.domain.usecase

import com.memorylink.domain.model.CalendarEvent
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** Result containing next all-day event (7-day window) and timed event (2-week window). */
data class NextEventsResult(
        val allDayEvent: CalendarEvent? = null,
        val timedEvent: CalendarEvent? = null
)

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

    /** Get next all-day and timed events from the provided list. */
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
