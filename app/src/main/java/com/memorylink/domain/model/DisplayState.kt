package com.memorylink.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Kiosk display states. See: 40-state-machine.md
 *
 * Time is NOT embedded here—UI reads live system time. Clock area shows time/date/all-day event;
 * event card shows next timed event.
 */
sealed class DisplayState {

        /** Common display settings shared by all states. */
        abstract val use24HourFormat: Boolean
        abstract val showYearInDate: Boolean

        /**
         * AWAKE_NO_EVENT: Within wake period but no future events in lookahead window. Display:
         * Full clock (72sp) + date (36sp), full brightness.
         */
        data class AwakeNoEvent(
                override val use24HourFormat: Boolean = false,
                override val showYearInDate: Boolean = true
        ) : DisplayState()

        /**
         * AWAKE_WITH_EVENT: Within wake period and at least one event exists in lookahead window.
         *
         * Layout:
         * - Clock area: Time + Date + optional all-day event (in AccentBlue)
         * - Event card: Next timed event (only shown if timedEventTitle is set)
         *
         * All-day event display rules:
         * - Today: "Today is {title}"
         * - Tomorrow: "Tomorrow is {title}"
         * - Future (within 7 days): "{Day of week} is {title}"
         *
         * Timed event display rules:
         * - Today: "At {time}, {title}"
         * - Tomorrow: "Tomorrow, at {time}, {title}"
         * - Future (within 2 weeks): "On {day}, {date} at {time}, {title}"
         *
         * @param allDayEventTitle Title of next all-day event, or null if none
         * @param allDayEventDate Date of all-day event, or null if today
         * @param timedEventTitle Title of next timed event, or null if none
         * @param timedEventTime Start time of timed event, or null if none
         * @param timedEventDate Date of timed event, or null if today
         */
        data class AwakeWithEvent(
                // All-day event (displays in clock area)
                val allDayEventTitle: String? = null,
                val allDayEventDate: LocalDate? = null,
                // Timed event (displays in event card)
                val timedEventTitle: String? = null,
                val timedEventTime: LocalTime? = null,
                val timedEventDate: LocalDate? = null,
                // Settings
                override val use24HourFormat: Boolean = false,
                override val showYearInDate: Boolean = true
        ) : DisplayState() {

                /** Returns true if there's an all-day event to display. */
                val hasAllDayEvent: Boolean
                        get() = allDayEventTitle != null

                /** Returns true if the all-day event is today (not a future day). */
                val isAllDayEventToday: Boolean
                        get() = allDayEventTitle != null && allDayEventDate == null

                /** Returns true if there's a timed event to display. */
                val hasTimedEvent: Boolean
                        get() = timedEventTitle != null

                /** Returns true if the timed event is today (not a future day). */
                val isTimedEventToday: Boolean
                        get() = timedEventTitle != null && timedEventDate == null
        }

        /**
         * SLEEP: Current time is within sleep period. Display: Dimmed clock + date (same layout as
         * AwakeNoEvent), 10% brightness.
         */
        data class Sleep(
                override val use24HourFormat: Boolean = false,
                override val showYearInDate: Boolean = true
        ) : DisplayState()
}
