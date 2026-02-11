package com.memorylink.domain.usecase

import com.memorylink.domain.model.CalendarEvent
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Use case for determining which event to display next.
 *
 * Implements the 2-hour preview window logic for all-day vs timed events.
 * See .clinerules/40-state-machine.md for the full algorithm.
 *
 * Rules:
 * 1. If a timed event is within 2-hour preview window, show it
 * 2. Otherwise, show all-day event if one exists
 * 3. Otherwise, show next timed event (even if >2 hours away)
 * 4. Return null if no events for today
 */
class GetNextEventUseCase @Inject constructor() {

    companion object {
        /**
         * Hours before a timed event when it supersedes all-day events.
         */
        const val PREVIEW_WINDOW_HOURS = 2L
    }

    /**
     * Get the next event to display.
     *
     * @param now Current date/time
     * @param events List of all calendar events (should already be filtered to today)
     * @return The event to display, or null if no events
     */
    operator fun invoke(now: LocalDateTime, events: List<CalendarEvent>): CalendarEvent? {
        if (events.isEmpty()) return null

        val today = now.toLocalDate()

        // Filter to today's events only
        val todayEvents = events.filter { it.startTime.toLocalDate() == today }
        if (todayEvents.isEmpty()) return null

        // Separate all-day and timed events
        // For timed events, only consider those that haven't started yet
        val timedEvents = todayEvents
            .filter { !it.isAllDay && it.startTime.isAfter(now) }
            .sortedBy { it.startTime }

        val allDayEvents = todayEvents.filter { it.isAllDay }

        // Find the next upcoming timed event
        val nextTimedEvent = timedEvents.firstOrNull()

        // Check if timed event is within 2-hour preview window
        if (nextTimedEvent != null) {
            val previewTime = nextTimedEvent.startTime.minusHours(PREVIEW_WINDOW_HOURS)
            if (!now.isBefore(previewTime)) {
                // We're within the preview window - show timed event
                return nextTimedEvent
            }
        }

        // Not in preview window - show all-day event if exists
        if (allDayEvents.isNotEmpty()) {
            // Return first all-day event (they're typically single per day)
            return allDayEvents.first()
        }

        // No all-day event - show next timed event even if >2 hours away
        return nextTimedEvent
    }
}
