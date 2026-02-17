package com.memorylink.domain.model

import java.time.LocalTime

/**
 * Represents the three display states of the kiosk screen. See .clinerules/40-state-machine.md for
 * the full state diagram.
 *
 * Note: Time is NOT embedded in DisplayState. The UI reads live system time directly for accurate
 * clock display. DisplayState only tracks the logical state (awake/sleep/event) and display
 * settings.
 */
sealed class DisplayState {

        /** Common display settings shared by all states. */
        abstract val use24HourFormat: Boolean
        abstract val showYearInDate: Boolean

        /**
         * AWAKE_NO_EVENT: Within wake period but no future events today. Display: Full clock (72sp)
         * + date (36sp), full brightness.
         */
        data class AwakeNoEvent(
                override val use24HourFormat: Boolean = false,
                override val showYearInDate: Boolean = true
        ) : DisplayState()

        /**
         * AWAKE_WITH_EVENT: Within wake period and next event exists today. Display: Clock + Date +
         * Event Card (title + time).
         *
         * @param nextEventTime The event start time, or null for all-day events.
         * ```
         *                      All-day events display as "TODAY IS [title]".
         * ```
         */
        data class AwakeWithEvent(
                val nextEventTitle: String,
                val nextEventTime: LocalTime?,
                override val use24HourFormat: Boolean = false,
                override val showYearInDate: Boolean = true
        ) : DisplayState()

        /**
         * SLEEP: Current time is within sleep period. Display: Dimmed clock + date (same layout as
         * AwakeNoEvent), 10% brightness.
         */
        data class Sleep(
                override val use24HourFormat: Boolean = false,
                override val showYearInDate: Boolean = true
        ) : DisplayState()
}
