package com.memorylink.domain.model

import java.time.LocalTime

/**
 * Sealed class hierarchy representing parsed [CONFIG] event results.
 *
 * Config events use the syntax: [CONFIG] TYPE VALUE
 *
 * See .clinerules/10-project-meta.md for full syntax documentation.
 */
sealed class ConfigResult {

    /**
     * Sleep time configuration.
     *
     * Syntax:
     * - `[CONFIG] SLEEP 21:00` - Static time (HH:MM)
     * - `[CONFIG] SLEEP SUNSET` - At sunset
     * - `[CONFIG] SLEEP SUNSET+30` - 30 min after sunset
     * - `[CONFIG] SLEEP SUNSET-15` - 15 min before sunset
     */
    sealed class SleepConfig : ConfigResult() {
        /** Static sleep time (HH:MM format). */
        data class StaticTime(val time: LocalTime) : SleepConfig()

        /** Dynamic sleep time relative to sunset. */
        data class DynamicTime(val reference: SolarReference, val offsetMinutes: Int) :
                SleepConfig()
    }

    /**
     * Wake time configuration.
     *
     * Syntax:
     * - `[CONFIG] WAKE 07:00` - Static time (HH:MM)
     * - `[CONFIG] WAKE SUNRISE` - At sunrise
     * - `[CONFIG] WAKE SUNRISE+15` - 15 min after sunrise
     * - `[CONFIG] WAKE SUNRISE-10` - 10 min before sunrise
     */
    sealed class WakeConfig : ConfigResult() {
        /** Static wake time (HH:MM format). */
        data class StaticTime(val time: LocalTime) : WakeConfig()

        /** Dynamic wake time relative to sunrise. */
        data class DynamicTime(val reference: SolarReference, val offsetMinutes: Int) :
                WakeConfig()
    }

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
     * Message area size configuration.
     *
     * Syntax: `[CONFIG] MESSAGE_SIZE 60`
     *
     * Controls what percentage of the screen height the event/message area occupies.
     *
     * @param percent Percentage of screen height 20-80
     */
    data class MessageSizeConfig(val percent: Int) : ConfigResult() {
        init {
            require(percent in 20..80) { "Message size must be 20-80%, got $percent" }
        }
    }

    /**
     * Unknown or invalid config (logged but ignored).
     *
     * @param rawText The original config text that couldn't be parsed
     * @param reason Why parsing failed
     */
    data class Invalid(val rawText: String, val reason: String) : ConfigResult()
}

/** Reference point for solar-based time calculations. */
enum class SolarReference {
    SUNRISE,
    SUNSET
}
