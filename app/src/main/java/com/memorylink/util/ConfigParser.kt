package com.memorylink.util

import android.util.Log
import com.memorylink.domain.model.ConfigResult
import com.memorylink.domain.model.ConfigResult.*
import com.memorylink.domain.model.SolarReference
import java.time.LocalTime

/**
 * Parser for [CONFIG] calendar event syntax.
 *
 * Parses event titles that start with "[CONFIG]" into structured ConfigResult objects.
 *
 * Supported syntax (case-insensitive):
 * - `[CONFIG] SLEEP 21:00` - Set sleep time (HH:MM)
 * - `[CONFIG] WAKE 07:00` - Set wake time (HH:MM)
 * - `[CONFIG] SLEEP SUNSET` - Sleep at sunset
 * - `[CONFIG] SLEEP SUNSET+30` - Sleep 30 min after sunset
 * - `[CONFIG] WAKE SUNRISE` - Wake at sunrise
 * - `[CONFIG] WAKE SUNRISE-15` - Wake 15 min before sunrise
 * - `[CONFIG] BRIGHTNESS 80` - Screen brightness (0-100)
 * - `[CONFIG] TIME_FORMAT 12` - 12-hour clock format
 * - `[CONFIG] TIME_FORMAT 24` - 24-hour clock format
 * - `[CONFIG] MESSAGE_SIZE 60` - Message area % of screen
 *
 * Invalid syntax is logged but returns ConfigResult.Invalid.
 *
 * See .clinerules/10-project-meta.md for full documentation.
 */
object ConfigParser {

    private const val TAG = "ConfigParser"
    private const val CONFIG_PREFIX = "[CONFIG]"

    // Regex patterns for parsing
    private val TIME_PATTERN = Regex("""^(\d{1,2}):(\d{2})$""")
    private val SOLAR_PATTERN = Regex("""^(SUNRISE|SUNSET)([+-]\d+)?$""", RegexOption.IGNORE_CASE)

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
     * @param title The full event title (e.g., "[CONFIG] SLEEP SUNSET+30")
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
            "MESSAGE_SIZE" -> parseMessageSizeConfig(configValue, title)
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
     * - Static time: "21:00", "9:30"
     * - Dynamic time: "SUNSET", "SUNSET+30", "SUNSET-15"
     */
    private fun parseSleepConfig(value: String, rawTitle: String): ConfigResult {
        if (value.isEmpty()) {
            return Invalid(rawTitle, "SLEEP requires a time value")
        }

        // Try static time first
        parseStaticTime(value)?.let {
            return SleepConfig.StaticTime(it)
        }

        // Try solar reference
        parseSolarTime(value)?.let { (reference, offset) ->
            return SleepConfig.DynamicTime(reference, offset)
        }

        return Invalid(rawTitle, "Invalid SLEEP value: $value")
    }

    /**
     * Parse WAKE configuration.
     *
     * Accepts:
     * - Static time: "07:00", "6:30"
     * - Dynamic time: "SUNRISE", "SUNRISE+15", "SUNRISE-10"
     */
    private fun parseWakeConfig(value: String, rawTitle: String): ConfigResult {
        if (value.isEmpty()) {
            return Invalid(rawTitle, "WAKE requires a time value")
        }

        // Try static time first
        parseStaticTime(value)?.let {
            return WakeConfig.StaticTime(it)
        }

        // Try solar reference
        parseSolarTime(value)?.let { (reference, offset) ->
            return WakeConfig.DynamicTime(reference, offset)
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
     * Parse MESSAGE_SIZE configuration.
     *
     * Accepts: Integer 20-80 (percentage of screen)
     */
    private fun parseMessageSizeConfig(value: String, rawTitle: String): ConfigResult {
        if (value.isEmpty()) {
            return Invalid(rawTitle, "MESSAGE_SIZE requires a value (20-80)")
        }

        val percent = value.toIntOrNull()
        if (percent == null) {
            return Invalid(rawTitle, "MESSAGE_SIZE must be a number: $value")
        }

        if (percent !in 20..80) {
            return Invalid(rawTitle, "MESSAGE_SIZE must be 20-80%, got $percent")
        }

        return MessageSizeConfig(percent)
    }

    /**
     * Parse a static time value (HH:MM format).
     *
     * @param value Time string like "21:00" or "9:30"
     * @return LocalTime if valid, null otherwise
     */
    private fun parseStaticTime(value: String): LocalTime? {
        val match = TIME_PATTERN.matchEntire(value) ?: return null

        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null

        if (hour !in 0..23 || minute !in 0..59) {
            return null
        }

        return try {
            LocalTime.of(hour, minute)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse a solar time value (SUNRISE/SUNSET with optional offset).
     *
     * @param value Solar string like "SUNSET", "SUNRISE+30", "SUNSET-15"
     * @return Pair of (SolarReference, offsetMinutes) if valid, null otherwise
     */
    private fun parseSolarTime(value: String): Pair<SolarReference, Int>? {
        val match = SOLAR_PATTERN.matchEntire(value) ?: return null

        val reference =
                when (match.groupValues[1].uppercase()) {
                    "SUNRISE" -> SolarReference.SUNRISE
                    "SUNSET" -> SolarReference.SUNSET
                    else -> return null
                }

        val offset =
                match.groupValues[2].let { offsetStr ->
                    if (offsetStr.isEmpty()) {
                        0
                    } else {
                        offsetStr.toIntOrNull() ?: return null
                    }
                }

        return reference to offset
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
