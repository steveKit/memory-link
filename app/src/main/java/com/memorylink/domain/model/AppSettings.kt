package com.memorylink.domain.model

import java.time.LocalTime

/**
 * Domain model for app configuration settings.
 *
 * Settings can be configured via:
 * 1. [CONFIG] calendar events (parsed remotely by family)
 * 2. Admin mode manual override
 *
 * Priority: Manual Override > Config Event > Default
 *
 * Default values use sunrise/sunset with static fallbacks.
 */
data class AppSettings(
        /**
         * Time to enter sleep mode (dimmed display).
         *
         * Default: SUNSET+30 (30 minutes after sunset) Fallback: 21:00 if location unavailable
         */
        val sleepTime: LocalTime = DEFAULT_SLEEP_TIME,

        /**
         * Time to wake from sleep mode (full display).
         *
         * Default: SUNRISE Fallback: 06:00 if location unavailable
         */
        val wakeTime: LocalTime = DEFAULT_WAKE_TIME,

        /**
         * Whether to use 24-hour time format.
         *
         * Default: false (12-hour with AM/PM)
         */
        val use24HourFormat: Boolean = false,

        /**
         * Screen brightness level (0-100).
         *
         * Default: 100 (full brightness during wake hours) Sleep mode overrides this to 10%.
         */
        val brightness: Int = 100,

        /**
         * Whether to show the year in the date display.
         *
         * Default: false (shows "Wednesday, February 11") When true: shows "Wednesday, February 11,
         * 2026"
         */
        val showYearInDate: Boolean = false,

        /**
         * Whether to show events during sleep mode.
         *
         * Default: false (clock only during sleep) When true: shows next event with dimmed styling
         * alongside the clock
         *
         * Note: API calls are still paused during sleep. Events shown are from local cache.
         */
        val showEventsDuringSleep: Boolean = false
) {
    companion object {
        /**
         * Fallback sleep time when sunrise/sunset API is unavailable. SUNSET+30 → 21:00 as
         * reasonable evening default.
         */
        val DEFAULT_SLEEP_TIME: LocalTime = LocalTime.of(21, 0)

        /**
         * Fallback wake time when sunrise/sunset API is unavailable. SUNRISE → 06:00 as reasonable
         * morning default.
         */
        val DEFAULT_WAKE_TIME: LocalTime = LocalTime.of(6, 0)
    }
}
