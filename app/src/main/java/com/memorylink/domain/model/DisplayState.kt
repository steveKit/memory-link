package com.memorylink.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Represents the three display states of the kiosk screen. See .clinerules/40-state-machine.md for
 * the full state diagram.
 */
sealed class DisplayState {

        /**
         * AWAKE_NO_EVENT: Within wake period but no future events today. Display: Full clock (72sp)
         * + date (36sp), full brightness.
         */
        data class AwakeNoEvent(
                val currentTime: LocalTime,
                val currentDate: LocalDate,
                val use24HourFormat: Boolean = false
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
                val currentTime: LocalTime,
                val currentDate: LocalDate,
                val nextEventTitle: String,
                val nextEventTime: LocalTime?,
                val use24HourFormat: Boolean = false
        ) : DisplayState()

        /**
         * SLEEP: Current time is within sleep period. Display: Dimmed clock + date (same layout as
         * AwakeNoEvent), 10% brightness.
         */
        data class Sleep(
                val currentTime: LocalTime,
                val currentDate: LocalDate,
                val use24HourFormat: Boolean = false
        ) : DisplayState()
}
