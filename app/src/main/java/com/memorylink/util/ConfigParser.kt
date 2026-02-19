package com.memorylink.util

import android.util.Log
import com.memorylink.domain.model.ConfigResult
import com.memorylink.domain.model.ConfigResult.*
import java.time.LocalTime

/**
 * Parser for [CONFIG] calendar event syntax.
 *
 * Supports:
 * - SLEEP/WAKE with static time (HH:MM or 12-hour format)
 * - BRIGHTNESS (0-100)
 * - TIME_FORMAT (12/24)
 *
 * Invalid syntax is logged and returns ConfigResult.Invalid.
 *
 * Time formats supported:
 * - 24-hour: "21:00", "9:30", "07:00"
 * - 12-hour with AM/PM: "9:00 PM", "9:00PM", "9PM", "9:00 pm", "7:30 AM"
 * - Ambiguous (no AM/PM): Context-dependent - WAKE assumes AM, SLEEP assumes PM
 *
 * Extra words in config values are trimmed before parsing (e.g., "21:00 bedtime" → "21:00").
 */
object ConfigParser {

    private const val TAG = "ConfigParser"
    private const val CONFIG_PREFIX = "[CONFIG]"

    // Regex patterns for parsing
    private val TIME_24H_PATTERN = Regex("""^(\d{1,2}):(\d{2})$""")
    // 12-hour with AM/PM: "9:00 PM", "9:00PM", "9PM", "9:30am", etc.
    private val TIME_12H_PATTERN =
            Regex("""^(\d{1,2})(?::(\d{2}))?\s*(AM|PM)$""", RegexOption.IGNORE_CASE)

    /**
     * Check if an event title is a config event.
     *
     * @param title Event title to check
     * @return true if the title starts with [CONFIG]
     */
    fun isConfigEvent(title: String): Boolean {
        return title.trim().uppercase().startsWith(CONFIG_PREFIX)
    }

    /**
     * Parse a config event title into a ConfigResult.
     *
     * @param title The full event title (e.g., "[CONFIG] SLEEP 21:00")
     * @return Parsed ConfigResult, or Invalid if parsing fails
     */
    fun parse(title: String): ConfigResult {
        val trimmed = title.trim()

        // Verify it's a config event
        if (!isConfigEvent(trimmed)) {
            return Invalid(title, "Does not start with [CONFIG]")
        }

        // Extract the part after [CONFIG]
        val configPart = trimmed.substring(CONFIG_PREFIX.length).trim()

        if (configPart.isEmpty()) {
            return Invalid(title, "Empty config after [CONFIG]")
        }

        // Split into type and value
        val parts = configPart.split(Regex("\\s+"), limit = 2)
        val configType = parts[0].uppercase()
        val configValue = parts.getOrNull(1)?.trim() ?: ""

        return when (configType) {
            "SLEEP" -> parseSleepConfig(configValue, title)
            "WAKE" -> parseWakeConfig(configValue, title)
            "BRIGHTNESS" -> parseBrightnessConfig(configValue, title)
            "TIME_FORMAT" -> parseTimeFormatConfig(configValue, title)
            else -> {
                Log.w(TAG, "Unknown config type: $configType")
                Invalid(title, "Unknown config type: $configType")
            }
        }
    }

    /**
     * Parse SLEEP configuration.
     *
     * Accepts:
     * - Static time (24h): "21:00", "9:30"
     * - Static time (12h): "9:00 PM", "9PM", "9:00pm"
     * - Ambiguous time (no AM/PM): Assumes PM for SLEEP (e.g., "9:00" → 21:00)
     *
     * Extra words are trimmed from the value before parsing.
     */
    private fun parseSleepConfig(value: String, rawTitle: String): ConfigResult {
        if (value.isEmpty()) {
            return Invalid(rawTitle, "SLEEP requires a time value")
        }

        // Extract time portion (trim extra words like "bedtime", "reminder", etc.)
        val cleanedValue = extractTimeValue(value)

        // Try static time with context: SLEEP assumes PM for ambiguous times
        parseStaticTimeWithContext(cleanedValue, assumePm = true)?.let {
            return SleepConfig(it)
        }

        return Invalid(rawTitle, "Invalid SLEEP value: $value")
    }

    /**
     * Parse WAKE configuration.
     *
     * Accepts:
     * - Static time (24h): "07:00", "6:30"
     * - Static time (12h): "7:00 AM", "7AM", "7:00am"
     * - Ambiguous time (no AM/PM): Assumes AM for WAKE (e.g., "7:00" → 07:00)
     *
     * Extra words are trimmed from the value before parsing.
     */
    private fun parseWakeConfig(value: String, rawTitle: String): ConfigResult {
        if (value.isEmpty()) {
            return Invalid(rawTitle, "WAKE requires a time value")
        }

        // Extract time portion (trim extra words like "alarm", "morning", etc.)
        val cleanedValue = extractTimeValue(value)

        // Try static time with context: WAKE assumes AM for ambiguous times
        parseStaticTimeWithContext(cleanedValue, assumePm = false)?.let {
            return WakeConfig(it)
        }

        return Invalid(rawTitle, "Invalid WAKE value: $value")
    }

    /**
     * Parse BRIGHTNESS configuration.
     *
     * Accepts: Integer 0-100
     */
    private fun parseBrightnessConfig(value: String, rawTitle: String): ConfigResult {
        if (value.isEmpty()) {
            return Invalid(rawTitle, "BRIGHTNESS requires a value (0-100)")
        }

        val percent = value.toIntOrNull()
        if (percent == null) {
            return Invalid(rawTitle, "BRIGHTNESS must be a number: $value")
        }

        if (percent !in 0..100) {
            return Invalid(rawTitle, "BRIGHTNESS must be 0-100, got $percent")
        }

        return BrightnessConfig(percent)
    }

    /**
     * Parse TIME_FORMAT configuration.
     *
     * Accepts: "12" or "24"
     */
    private fun parseTimeFormatConfig(value: String, rawTitle: String): ConfigResult {
        return when (value) {
            "12" -> TimeFormatConfig(use24Hour = false)
            "24" -> TimeFormatConfig(use24Hour = true)
            else -> Invalid(rawTitle, "TIME_FORMAT must be 12 or 24, got: $value")
        }
    }

    /**
     * Extract the time value from a string that may contain extra words.
     *
     * Examples:
     * - "21:00 bedtime" → "21:00"
     * - "9:00 PM set alarm" → "9:00 PM"
     * - "9PM tonight" → "9PM"
     *
     * @param value The raw config value that may contain extra words
     * @return The cleaned time string
     */
    private fun extractTimeValue(value: String): String {
        val trimmed = value.trim()

        // Try to match 12-hour pattern with AM/PM (may include space before AM/PM)
        // Pattern: digits, optional :MM, optional space, AM/PM
        val time12hExtract = Regex("""^(\d{1,2}(?::\d{2})?\s*(?:AM|PM))""", RegexOption.IGNORE_CASE)
        time12hExtract.find(trimmed)?.let {
            return it.groupValues[1]
        }

        // Try to match 24-hour pattern (just HH:MM)
        val time24hExtract = Regex("""^(\d{1,2}:\d{2})""")
        time24hExtract.find(trimmed)?.let {
            return it.groupValues[1]
        }

        // No recognized pattern - return first word only (could be just a number like "9")
        val firstWord = trimmed.split(Regex("\\s+")).firstOrNull() ?: trimmed
        return firstWord
    }

    /**
     * Parse a static time value with context-dependent AM/PM assumption.
     *
     * Supports:
     * - 24-hour format: "21:00", "9:30", "07:00"
     * - 12-hour with AM/PM: "9:00 PM", "9:00PM", "9PM", "9:30am"
     * - Ambiguous (no AM/PM): Uses context (WAKE=AM, SLEEP=PM)
     *
     * @param value Time string
     * @param assumePm If true, ambiguous times (1-12 without AM/PM) assume PM; otherwise AM
     * @return LocalTime if valid, null otherwise
     */
    private fun parseStaticTimeWithContext(value: String, assumePm: Boolean): LocalTime? {
        // First, try 12-hour format with explicit AM/PM
        parse12HourTime(value)?.let {
            return it
        }

        // Try 24-hour format
        val match24h = TIME_24H_PATTERN.matchEntire(value)
        if (match24h != null) {
            val hour = match24h.groupValues[1].toIntOrNull() ?: return null
            val minute = match24h.groupValues[2].toIntOrNull() ?: return null

            if (minute !in 0..59) return null

            // Check if this is clearly a 24-hour time (hour >= 13)
            if (hour in 13..23) {
                return try {
                    LocalTime.of(hour, minute)
                } catch (e: Exception) {
                    null
                }
            }

            // Hour is 0-12, could be 24h or ambiguous 12h
            if (hour == 0) {
                // 0:xx is clearly 24-hour (midnight)
                return try {
                    LocalTime.of(0, minute)
                } catch (e: Exception) {
                    null
                }
            }

            // Hour is 1-12, ambiguous - apply context
            val adjustedHour =
                    if (assumePm && hour in 1..11) {
                        hour + 12 // Convert to PM (e.g., 9 → 21)
                    } else if (hour == 12 && !assumePm) {
                        0 // 12 AM is midnight (00:00)
                    } else if (hour == 12 && assumePm) {
                        12 // 12 PM is noon (12:00)
                    } else {
                        hour // AM, keep as-is (e.g., 7 stays 7)
                    }

            return try {
                LocalTime.of(adjustedHour, minute)
            } catch (e: Exception) {
                null
            }
        }

        return null
    }

    /**
     * Parse a 12-hour time format with explicit AM/PM.
     *
     * @param value Time string like "9:00 PM", "9PM", "9:30am"
     * @return LocalTime if valid, null otherwise
     */
    private fun parse12HourTime(value: String): LocalTime? {
        val match = TIME_12H_PATTERN.matchEntire(value) ?: return null

        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute =
                match.groupValues[2].let {
                    if (it.isEmpty()) 0 else it.toIntOrNull() ?: return null
                }
        val amPm = match.groupValues[3].uppercase()

        if (hour !in 1..12 || minute !in 0..59) return null

        // Convert to 24-hour format
        val hour24 =
                when {
                    amPm == "AM" && hour == 12 -> 0 // 12 AM = midnight
                    amPm == "PM" && hour == 12 -> 12 // 12 PM = noon
                    amPm == "PM" -> hour + 12 // 1-11 PM → 13-23
                    else -> hour // 1-11 AM stays as-is
                }

        return try {
            LocalTime.of(hour24, minute)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse multiple config events and return all valid results.
     *
     * Invalid configs are logged but excluded from the result.
     *
     * @param titles List of event titles to parse
     * @return List of valid ConfigResults (excludes Invalid results)
     */
    fun parseAll(titles: List<String>): List<ConfigResult> {
        return titles
                .filter { isConfigEvent(it) }
                .map { parse(it) }
                .also { results ->
                    // Log invalid configs
                    results.filterIsInstance<Invalid>().forEach { invalid ->
                        Log.w(TAG, "Invalid config: '${invalid.rawText}' - ${invalid.reason}")
                    }
                }
                .filterNot { it is Invalid }
    }
}
