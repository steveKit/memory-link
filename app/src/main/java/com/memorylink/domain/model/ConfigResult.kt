package com.memorylink.domain.model

import java.time.LocalTime

/**
 * Sealed class hierarchy representing parsed [CONFIG] event results.
 *
 * Config events use the syntax: [CONFIG] TYPE VALUE
 *
 * Supported configurations:
 * - SLEEP HH:MM - Sleep time (static time only)
 * - WAKE HH:MM - Wake time (static time only)
 * - BRIGHTNESS 0-100 - Screen brightness
 * - TIME_FORMAT 12|24 - Clock format
 * - SHOW/HIDE YEAR - Show/hide year in date display
 * - SHOW/HIDE SLEEP_EVENTS - Show/hide events during sleep mode
 * - SHOW/HIDE HOLIDAYS - Show/hide holiday calendar events
 */
sealed class ConfigResult {

    /**
     * Sleep time configuration.
     *
     * Syntax: `[CONFIG] SLEEP 21:00` - Static time (HH:MM or 12h format)
     */
    data class SleepConfig(val time: LocalTime) : ConfigResult()

    /**
     * Wake time configuration.
     *
     * Syntax: `[CONFIG] WAKE 07:00` - Static time (HH:MM or 12h format)
     */
    data class WakeConfig(val time: LocalTime) : ConfigResult()

    /**
     * Brightness configuration.
     *
     * Syntax: `[CONFIG] BRIGHTNESS 80`
     *
     * @param percent Brightness level 0-100
     */
    data class BrightnessConfig(val percent: Int) : ConfigResult() {
        init {
            require(percent in 0..100) { "Brightness must be 0-100, got $percent" }
        }
    }

    /**
     * Time format configuration.
     *
     * Syntax: `[CONFIG] TIME_FORMAT 12` or `[CONFIG] TIME_FORMAT 24`
     *
     * @param use24Hour True for 24-hour format, false for 12-hour
     */
    data class TimeFormatConfig(val use24Hour: Boolean) : ConfigResult()

    /**
     * Show/hide year in date display.
     *
     * Syntax: `[CONFIG] SHOW YEAR` or `[CONFIG] HIDE YEAR`
     *
     * @param show True to show year, false to hide
     */
    data class ShowYearConfig(val show: Boolean) : ConfigResult()

    /**
     * Show/hide events during sleep mode.
     *
     * Syntax: `[CONFIG] SHOW SLEEP_EVENTS` or `[CONFIG] HIDE SLEEP_EVENTS`
     *
     * @param show True to show events during sleep, false for clock only
     */
    data class ShowSleepEventsConfig(val show: Boolean) : ConfigResult()

    /**
     * Show/hide holiday calendar events.
     *
     * Syntax: `[CONFIG] SHOW HOLIDAYS` or `[CONFIG] HIDE HOLIDAYS`
     *
     * Note: Only applies if a holiday calendar is configured in admin mode.
     *
     * @param show True to show holidays, false to hide
     */
    data class ShowHolidaysConfig(val show: Boolean) : ConfigResult()

    /**
     * Unknown or invalid config (logged but ignored).
     *
     * @param rawText The original config text that couldn't be parsed
     * @param reason Why parsing failed
     */
    data class Invalid(val rawText: String, val reason: String) : ConfigResult()
}
