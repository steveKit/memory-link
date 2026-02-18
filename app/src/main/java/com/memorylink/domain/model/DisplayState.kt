package com.memorylink.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Represents an all-day event for display purposes.
 *
 * @param title Event title
 * @param startDate Start date, or null if today (ongoing multi-day event)
 * @param endDate End date (exclusive) for multi-day events, or null if single-day
 */
data class AllDayEventInfo(
        val title: String,
        val startDate: LocalDate? = null,
        val endDate: LocalDate? = null
)

/**
 * Kiosk display states. See: 40-state-machine.md
 *
 * Time is NOT embedded here—UI reads live system time. Clock area shows time/date/all-day events;
 * event card shows next timed event.
 */
sealed class DisplayState {

        /** Common display settings shared by all states. */
        abstract val use24HourFormat: Boolean
        abstract val showYearInDate: Boolean

        /**
         * Screen brightness (0-100).
         *
         * Per clinerules/20-android.md:
         * - Awake states: Use configured brightness from settings
         * - Sleep state: Always 10% (SLEEP_BRIGHTNESS)
         */
        abstract val brightness: Int

        companion object {
                /** Sleep mode brightness: 10% per clinerules/20-android.md */
                const val SLEEP_BRIGHTNESS = 10

                /** Default awake brightness when not configured */
                const val DEFAULT_BRIGHTNESS = 100
        }

        /**
         * AWAKE_NO_EVENT: Within wake period but no future events in lookahead window. Display:
         * Full clock (72sp) + date (36sp), full brightness.
         */
        data class AwakeNoEvent(
                override val use24HourFormat: Boolean = false,
                override val showYearInDate: Boolean = true,
                override val brightness: Int = DEFAULT_BRIGHTNESS
        ) : DisplayState()

        /**
         * AWAKE_WITH_EVENT: Within wake period and at least one event exists in lookahead window.
         *
         * Layout:
         * - Clock area: Time + Date + all-day events (each on separate line, in AccentBlue)
         * - Event card: Next timed event (only shown if timedEventTitle is set)
         *
         * All-day event display rules (each event rendered as a separate line):
         * - Today (single-day): "Today is {title}"
         * - Tomorrow (single-day): "Tomorrow is {title}"
         * - Future (within 7 days, single-day): "{Day of week} is {title}"
         * - Ongoing multi-day (started before today): "{title} until {end day/date}"
         *
         * Timed event display rules:
         * - Today: "At {time}, {title}"
         * - Tomorrow: "Tomorrow, at {time}, {title}"
         * - Future (within 2 weeks): "On {day}, {date} at {time}, {title}"
         *
         * @param allDayEvents List of all-day events to display (holidays first, then by date)
         * @param timedEventTitle Title of next timed event, or null if none
         * @param timedEventTime Start time of timed event, or null if none
         * @param timedEventDate Date of timed event, or null if today
         */
        data class AwakeWithEvent(
                // All-day events (display in clock area, each on separate line)
                val allDayEvents: List<AllDayEventInfo> = emptyList(),
                // Timed event (displays in event card)
                val timedEventTitle: String? = null,
                val timedEventTime: LocalTime? = null,
                val timedEventDate: LocalDate? = null,
                // Settings
                override val use24HourFormat: Boolean = false,
                override val showYearInDate: Boolean = true,
                override val brightness: Int = DEFAULT_BRIGHTNESS
        ) : DisplayState() {

                /** Returns true if there are all-day events to display. */
                val hasAllDayEvent: Boolean
                        get() = allDayEvents.isNotEmpty()

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
         *
         * When showEventsDuringSleep is enabled in settings, event data will be populated and
         * displayed with dimmed styling alongside the clock.
         *
         * @param allDayEvents List of all-day events to display when enabled
         * @param timedEventTitle Title of next timed event, or null if none/disabled
         * @param timedEventTime Start time of timed event, or null if none
         * @param timedEventDate Date of timed event, or null if today
         */
        data class Sleep(
                // All-day events (display in clock area when enabled)
                val allDayEvents: List<AllDayEventInfo> = emptyList(),
                // Timed event (displays in event card when enabled)
                val timedEventTitle: String? = null,
                val timedEventTime: LocalTime? = null,
                val timedEventDate: LocalDate? = null,
                // Settings
                override val use24HourFormat: Boolean = false,
                override val showYearInDate: Boolean = true,
                override val brightness: Int = SLEEP_BRIGHTNESS
        ) : DisplayState() {

                /** Returns true if there are all-day events to display. */
                val hasAllDayEvent: Boolean
                        get() = allDayEvents.isNotEmpty()

                /** Returns true if there's a timed event to display. */
                val hasTimedEvent: Boolean
                        get() = timedEventTitle != null
        }
}
