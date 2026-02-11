package com.memorylink.domain.model

import java.time.LocalDateTime

/**
 * Domain model representing a calendar event.
 *
 * This is separate from the Room entity (EventEntity) to maintain clean architecture boundaries.
 * The repository layer handles mapping between entity and domain model.
 */
data class CalendarEvent(
    /**
     * Unique event ID from Google Calendar.
     */
    val id: String,

    /**
     * Event title/summary to display.
     */
    val title: String,

    /**
     * Event start time.
     * For all-day events, this represents the start of the day.
     */
    val startTime: LocalDateTime,

    /**
     * Event end time.
     * For all-day events, this represents the end of the day.
     */
    val endTime: LocalDateTime,

    /**
     * Whether this is an all-day event.
     *
     * All-day events have special display rules:
     * - Display from wake_time to sleep_time (not midnight to midnight)
     * - Superseded by timed events 2 hours before their start time
     * - Return to showing after timed event passes (if no more timed events)
     */
    val isAllDay: Boolean = false
)
