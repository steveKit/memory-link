package com.memorylink.util

import com.memorylink.domain.model.ConfigResult.*
import com.memorylink.domain.model.SolarReference
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ConfigParser.
 *
 * Per .clinerules/10-project-meta.md:
 * - Unit Tests: 100% coverage on config parser ([CONFIG] event syntax)
 *
 * Tests all config patterns:
 * - SLEEP/WAKE with static times (HH:MM)
 * - SLEEP/WAKE with dynamic times (SUNRISE/SUNSET with offsets)
 * - BRIGHTNESS (0-100)
 * - TIME_FORMAT (12/24)
 * - Invalid syntax handling
 */
class ConfigParserTest {

    // region isConfigEvent tests

    @Test
    fun `isConfigEvent returns true for valid config event`() {
        assertTrue(ConfigParser.isConfigEvent("[CONFIG] SLEEP 21:00"))
    }

    @Test
    fun `isConfigEvent returns true regardless of case`() {
        assertTrue(ConfigParser.isConfigEvent("[config] sleep 21:00"))
        assertTrue(ConfigParser.isConfigEvent("[Config] Sleep 21:00"))
        assertTrue(ConfigParser.isConfigEvent("[CONFIG] SLEEP 21:00"))
    }

    @Test
    fun `isConfigEvent returns true with leading whitespace`() {
        assertTrue(ConfigParser.isConfigEvent("  [CONFIG] SLEEP 21:00"))
    }

    @Test
    fun `isConfigEvent returns false for non-config event`() {
        assertFalse(ConfigParser.isConfigEvent("Doctor Appointment"))
        assertFalse(ConfigParser.isConfigEvent("Birthday Party"))
        assertFalse(ConfigParser.isConfigEvent("CONFIG SLEEP 21:00")) // Missing brackets
    }

    @Test
    fun `isConfigEvent returns false for empty string`() {
        assertFalse(ConfigParser.isConfigEvent(""))
    }

    // endregion

    // region SLEEP config tests

    @Test
    fun `SLEEP parses static time HH-MM format`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP 21:00")
        assertTrue(result is SleepConfig.StaticTime)
        assertEquals(LocalTime.of(21, 0), (result as SleepConfig.StaticTime).time)
    }

    @Test
    fun `SLEEP parses static time with single digit hour`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP 9:30")
        assertTrue(result is SleepConfig.StaticTime)
        assertEquals(LocalTime.of(9, 30), (result as SleepConfig.StaticTime).time)
    }

    @Test
    fun `SLEEP parses SUNSET without offset`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP SUNSET")
        assertTrue(result is SleepConfig.DynamicTime)
        val dynamic = result as SleepConfig.DynamicTime
        assertEquals(SolarReference.SUNSET, dynamic.reference)
        assertEquals(0, dynamic.offsetMinutes)
    }

    @Test
    fun `SLEEP parses SUNSET with positive offset`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP SUNSET+30")
        assertTrue(result is SleepConfig.DynamicTime)
        val dynamic = result as SleepConfig.DynamicTime
        assertEquals(SolarReference.SUNSET, dynamic.reference)
        assertEquals(30, dynamic.offsetMinutes)
    }

    @Test
    fun `SLEEP parses SUNSET with negative offset`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP SUNSET-15")
        assertTrue(result is SleepConfig.DynamicTime)
        val dynamic = result as SleepConfig.DynamicTime
        assertEquals(SolarReference.SUNSET, dynamic.reference)
        assertEquals(-15, dynamic.offsetMinutes)
    }

    @Test
    fun `SLEEP parses SUNRISE with offset`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP SUNRISE+60")
        assertTrue(result is SleepConfig.DynamicTime)
        val dynamic = result as SleepConfig.DynamicTime
        assertEquals(SolarReference.SUNRISE, dynamic.reference)
        assertEquals(60, dynamic.offsetMinutes)
    }

    @Test
    fun `SLEEP returns invalid for missing value`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP")
        assertTrue(result is Invalid)
    }

    @Test
    fun `SLEEP returns invalid for invalid time format`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP 25:00")
        assertTrue(result is Invalid)
    }

    @Test
    fun `SLEEP returns invalid for non-time value`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP abc")
        assertTrue(result is Invalid)
    }

    // endregion

    // region WAKE config tests

    @Test
    fun `WAKE parses static time`() {
        val result = ConfigParser.parse("[CONFIG] WAKE 07:00")
        assertTrue(result is WakeConfig.StaticTime)
        assertEquals(LocalTime.of(7, 0), (result as WakeConfig.StaticTime).time)
    }

    @Test
    fun `WAKE parses SUNRISE without offset`() {
        val result = ConfigParser.parse("[CONFIG] WAKE SUNRISE")
        assertTrue(result is WakeConfig.DynamicTime)
        val dynamic = result as WakeConfig.DynamicTime
        assertEquals(SolarReference.SUNRISE, dynamic.reference)
        assertEquals(0, dynamic.offsetMinutes)
    }

    @Test
    fun `WAKE parses SUNRISE with positive offset`() {
        val result = ConfigParser.parse("[CONFIG] WAKE SUNRISE+15")
        assertTrue(result is WakeConfig.DynamicTime)
        val dynamic = result as WakeConfig.DynamicTime
        assertEquals(SolarReference.SUNRISE, dynamic.reference)
        assertEquals(15, dynamic.offsetMinutes)
    }

    @Test
    fun `WAKE parses SUNRISE with negative offset`() {
        val result = ConfigParser.parse("[CONFIG] WAKE SUNRISE-10")
        assertTrue(result is WakeConfig.DynamicTime)
        val dynamic = result as WakeConfig.DynamicTime
        assertEquals(SolarReference.SUNRISE, dynamic.reference)
        assertEquals(-10, dynamic.offsetMinutes)
    }

    @Test
    fun `WAKE parses SUNSET with offset`() {
        val result = ConfigParser.parse("[CONFIG] WAKE SUNSET-120")
        assertTrue(result is WakeConfig.DynamicTime)
        val dynamic = result as WakeConfig.DynamicTime
        assertEquals(SolarReference.SUNSET, dynamic.reference)
        assertEquals(-120, dynamic.offsetMinutes)
    }

    @Test
    fun `WAKE returns invalid for missing value`() {
        val result = ConfigParser.parse("[CONFIG] WAKE")
        assertTrue(result is Invalid)
    }

    // endregion

    // region BRIGHTNESS config tests

    @Test
    fun `BRIGHTNESS parses valid value`() {
        val result = ConfigParser.parse("[CONFIG] BRIGHTNESS 80")
        assertTrue(result is BrightnessConfig)
        assertEquals(80, (result as BrightnessConfig).percent)
    }

    @Test
    fun `BRIGHTNESS parses minimum value`() {
        val result = ConfigParser.parse("[CONFIG] BRIGHTNESS 0")
        assertTrue(result is BrightnessConfig)
        assertEquals(0, (result as BrightnessConfig).percent)
    }

    @Test
    fun `BRIGHTNESS parses maximum value`() {
        val result = ConfigParser.parse("[CONFIG] BRIGHTNESS 100")
        assertTrue(result is BrightnessConfig)
        assertEquals(100, (result as BrightnessConfig).percent)
    }

    @Test
    fun `BRIGHTNESS returns invalid for value over 100`() {
        val result = ConfigParser.parse("[CONFIG] BRIGHTNESS 150")
        assertTrue(result is Invalid)
    }

    @Test
    fun `BRIGHTNESS returns invalid for negative value`() {
        val result = ConfigParser.parse("[CONFIG] BRIGHTNESS -10")
        assertTrue(result is Invalid)
    }

    @Test
    fun `BRIGHTNESS returns invalid for non-numeric value`() {
        val result = ConfigParser.parse("[CONFIG] BRIGHTNESS high")
        assertTrue(result is Invalid)
    }

    @Test
    fun `BRIGHTNESS returns invalid for missing value`() {
        val result = ConfigParser.parse("[CONFIG] BRIGHTNESS")
        assertTrue(result is Invalid)
    }

    // endregion

    // region TIME_FORMAT config tests

    @Test
    fun `TIME_FORMAT parses 12-hour format`() {
        val result = ConfigParser.parse("[CONFIG] TIME_FORMAT 12")
        assertTrue(result is TimeFormatConfig)
        assertFalse((result as TimeFormatConfig).use24Hour)
    }

    @Test
    fun `TIME_FORMAT parses 24-hour format`() {
        val result = ConfigParser.parse("[CONFIG] TIME_FORMAT 24")
        assertTrue(result is TimeFormatConfig)
        assertTrue((result as TimeFormatConfig).use24Hour)
    }

    @Test
    fun `TIME_FORMAT returns invalid for other values`() {
        val result = ConfigParser.parse("[CONFIG] TIME_FORMAT 13")
        assertTrue(result is Invalid)
    }

    @Test
    fun `TIME_FORMAT returns invalid for missing value`() {
        val result = ConfigParser.parse("[CONFIG] TIME_FORMAT")
        assertTrue(result is Invalid)
    }

    // endregion

    // region Invalid config tests

    @Test
    fun `parse returns invalid for non-config event`() {
        val result = ConfigParser.parse("Doctor Appointment")
        assertTrue(result is Invalid)
    }

    @Test
    fun `parse returns invalid for unknown config type`() {
        val result = ConfigParser.parse("[CONFIG] UNKNOWN 123")
        assertTrue(result is Invalid)
        assertTrue((result as Invalid).reason.contains("Unknown config type"))
    }

    @Test
    fun `parse returns invalid for empty config`() {
        val result = ConfigParser.parse("[CONFIG]")
        assertTrue(result is Invalid)
    }

    @Test
    fun `parse returns invalid for config with only spaces`() {
        val result = ConfigParser.parse("[CONFIG]   ")
        assertTrue(result is Invalid)
    }

    @Test
    fun `invalid result contains original text`() {
        val result = ConfigParser.parse("[CONFIG] BRIGHTNESS invalid")
        assertTrue(result is Invalid)
        assertEquals("[CONFIG] BRIGHTNESS invalid", (result as Invalid).rawText)
    }

    // endregion

    // region Case insensitivity tests

    @Test
    fun `parses lowercase config prefix`() {
        val result = ConfigParser.parse("[config] sleep 21:00")
        assertTrue(result is SleepConfig.StaticTime)
    }

    @Test
    fun `parses mixed case config type`() {
        val result = ConfigParser.parse("[CONFIG] Sleep 21:00")
        assertTrue(result is SleepConfig.StaticTime)
    }

    @Test
    fun `parses lowercase solar reference`() {
        val result = ConfigParser.parse("[CONFIG] WAKE sunrise")
        assertTrue(result is WakeConfig.DynamicTime)
    }

    @Test
    fun `parses mixed case solar reference with offset`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP Sunset+30")
        assertTrue(result is SleepConfig.DynamicTime)
    }

    // endregion

    // region Whitespace handling tests

    @Test
    fun `handles leading whitespace`() {
        val result = ConfigParser.parse("  [CONFIG] SLEEP 21:00")
        assertTrue(result is SleepConfig.StaticTime)
    }

    @Test
    fun `handles trailing whitespace`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP 21:00  ")
        assertTrue(result is SleepConfig.StaticTime)
    }

    @Test
    fun `handles multiple spaces between parts`() {
        val result = ConfigParser.parse("[CONFIG]   BRIGHTNESS   80")
        assertTrue(result is BrightnessConfig)
    }

    // endregion

    // region parseAll tests

    @Test
    fun `parseAll filters and parses only config events`() {
        val titles =
                listOf(
                        "[CONFIG] SLEEP 21:00",
                        "Doctor Appointment",
                        "[CONFIG] WAKE 07:00",
                        "Birthday Party"
                )
        val results = ConfigParser.parseAll(titles)
        assertEquals(2, results.size)
        assertTrue(results[0] is SleepConfig.StaticTime)
        assertTrue(results[1] is WakeConfig.StaticTime)
    }

    @Test
    fun `parseAll excludes invalid configs from results`() {
        val titles = listOf("[CONFIG] SLEEP 21:00", "[CONFIG] INVALID abc", "[CONFIG] WAKE 07:00")
        val results = ConfigParser.parseAll(titles)
        assertEquals(2, results.size)
    }

    @Test
    fun `parseAll returns empty list for no config events`() {
        val titles = listOf("Doctor", "Birthday")
        val results = ConfigParser.parseAll(titles)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parseAll returns empty list for empty input`() {
        val results = ConfigParser.parseAll(emptyList())
        assertTrue(results.isEmpty())
    }

    // endregion

    // region Edge cases tests

    @Test
    fun `handles midnight time`() {
        val result = ConfigParser.parse("[CONFIG] WAKE 00:00")
        assertTrue(result is WakeConfig.StaticTime)
        assertEquals(LocalTime.of(0, 0), (result as WakeConfig.StaticTime).time)
    }

    @Test
    fun `handles 23-59 time`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP 23:59")
        assertTrue(result is SleepConfig.StaticTime)
        assertEquals(LocalTime.of(23, 59), (result as SleepConfig.StaticTime).time)
    }

    @Test
    fun `rejects hour 24`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP 24:00")
        assertTrue(result is Invalid)
    }

    @Test
    fun `rejects minute 60`() {
        val result = ConfigParser.parse("[CONFIG] SLEEP 12:60")
        assertTrue(result is Invalid)
    }

    @Test
    fun `handles large solar offset`() {
        val result = ConfigParser.parse("[CONFIG] WAKE SUNRISE+120")
        assertTrue(result is WakeConfig.DynamicTime)
        assertEquals(120, (result as WakeConfig.DynamicTime).offsetMinutes)
    }

    // endregion
}
