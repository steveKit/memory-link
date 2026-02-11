package com.memorylink.data.repository

import android.util.Log
import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.remote.SunriseSunsetApi
import com.memorylink.domain.model.AppSettings
import com.memorylink.domain.model.SolarReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for aggregating app settings from multiple sources.
 *
 * Priority (highest to lowest):
 * 1. Manual overrides (set in admin mode)
 * 2. [CONFIG] calendar event settings
 * 3. Default values
 *
 * For dynamic times (SUNRISE/SUNSET), the SunriseSunsetApi is used
 * to resolve the actual time, with fallbacks to defaults.
 *
 * See .clinerules/10-project-meta.md for config documentation.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val sunriseSunsetApi: SunriseSunsetApi
) {
    companion object {
        private const val TAG = "SettingsRepository"
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }

    /**
     * Current resolved settings.
     * Updated when config events are processed or manual settings change.
     */
    private val _settings = MutableStateFlow(AppSettings())
    val settings: Flow<AppSettings> = _settings.asStateFlow()

    /**
     * Get current settings (non-Flow version for synchronous access).
     */
    val currentSettings: AppSettings
        get() = _settings.value

    /**
     * Refresh settings by re-evaluating all sources.
     * Call this after config events are processed or manual settings change.
     *
     * @return The updated AppSettings
     */
    suspend fun refreshSettings(): AppSettings {
        val newSettings = buildSettings()
        _settings.value = newSettings
        Log.d(TAG, "Settings refreshed: $newSettings")
        return newSettings
    }

    /**
     * Build AppSettings by combining all sources with priority:
     * Manual Override > Config Event > Default
     */
    private suspend fun buildSettings(): AppSettings {
        return AppSettings(
            sleepTime = resolveSleepTime(),
            wakeTime = resolveWakeTime(),
            use24HourFormat = resolveTimeFormat(),
            brightness = resolveBrightness(),
            fontSize = resolveFontSize(),
            messageAreaPercent = resolveMessageSize()
        )
    }

    /**
     * Resolve sleep time with priority:
     * 1. Manual override
     * 2. Config event (static or dynamic)
     * 3. Default (SUNSET+30 fallback to 21:00)
     */
    private suspend fun resolveSleepTime(): LocalTime {
        // Check manual override first
        tokenStorage.manualSleepTime?.let { timeStr ->
            parseTime(timeStr)?.let { time ->
                Log.d(TAG, "Using manual sleep time: $time")
                return time
            }
        }

        // Check config event static time
        tokenStorage.configSleepTime?.let { timeStr ->
            parseTime(timeStr)?.let { time ->
                Log.d(TAG, "Using config sleep time: $time")
                return time
            }
        }

        // Check config event solar time
        tokenStorage.configSleepSolarRef?.let { solarRef ->
            val offset = tokenStorage.configSleepSolarOffset
            val resolvedTime = resolveSolarTime(solarRef, offset, AppSettings.DEFAULT_SLEEP_TIME)
            Log.d(TAG, "Using config solar sleep time: $solarRef$offset -> $resolvedTime")
            return resolvedTime
        }

        // Default: SUNSET+30 (fallback to 21:00)
        val defaultSolar = resolveSolarTime("SUNSET", 30, AppSettings.DEFAULT_SLEEP_TIME)
        Log.d(TAG, "Using default sleep time: SUNSET+30 -> $defaultSolar")
        return defaultSolar
    }

    /**
     * Resolve wake time with priority:
     * 1. Manual override
     * 2. Config event (static or dynamic)
     * 3. Default (SUNRISE fallback to 06:00)
     */
    private suspend fun resolveWakeTime(): LocalTime {
        // Check manual override first
        tokenStorage.manualWakeTime?.let { timeStr ->
            parseTime(timeStr)?.let { time ->
                Log.d(TAG, "Using manual wake time: $time")
                return time
            }
        }

        // Check config event static time
        tokenStorage.configWakeTime?.let { timeStr ->
            parseTime(timeStr)?.let { time ->
                Log.d(TAG, "Using config wake time: $time")
                return time
            }
        }

        // Check config event solar time
        tokenStorage.configWakeSolarRef?.let { solarRef ->
            val offset = tokenStorage.configWakeSolarOffset
            val resolvedTime = resolveSolarTime(solarRef, offset, AppSettings.DEFAULT_WAKE_TIME)
            Log.d(TAG, "Using config solar wake time: $solarRef$offset -> $resolvedTime")
            return resolvedTime
        }

        // Default: SUNRISE (fallback to 06:00)
        val defaultSolar = resolveSolarTime("SUNRISE", 0, AppSettings.DEFAULT_WAKE_TIME)
        Log.d(TAG, "Using default wake time: SUNRISE -> $defaultSolar")
        return defaultSolar
    }

    /**
     * Resolve solar time (SUNRISE/SUNSET) with offset.
     *
     * @param solarRef "SUNRISE" or "SUNSET"
     * @param offsetMinutes Offset in minutes (can be negative)
     * @param fallback Fallback time if API fails
     * @return Resolved LocalTime
     */
    private suspend fun resolveSolarTime(
        solarRef: String,
        offsetMinutes: Int,
        fallback: LocalTime
    ): LocalTime {
        val baseTime = when (solarRef.uppercase()) {
            "SUNRISE" -> sunriseSunsetApi.getSunrise(fallback)
            "SUNSET" -> sunriseSunsetApi.getSunset(fallback)
            else -> {
                Log.w(TAG, "Unknown solar reference: $solarRef, using fallback")
                fallback
            }
        }

        return if (offsetMinutes != 0) {
            baseTime.plusMinutes(offsetMinutes.toLong())
        } else {
            baseTime
        }
    }

    /**
     * Resolve time format with priority:
     * 1. Manual override
     * 2. Config event
     * 3. Default (12-hour)
     */
    private fun resolveTimeFormat(): Boolean {
        // Manual override
        tokenStorage.manualUse24HourFormat?.let { format ->
            Log.d(TAG, "Using manual time format: ${if (format) "24h" else "12h"}")
            return format
        }

        // Config event
        tokenStorage.configUse24HourFormat?.let { format ->
            Log.d(TAG, "Using config time format: ${if (format) "24h" else "12h"}")
            return format
        }

        // Default: 12-hour
        Log.d(TAG, "Using default time format: 12h")
        return false
    }

    /**
     * Resolve brightness with priority:
     * 1. Manual override
     * 2. Config event
     * 3. Default (100%)
     */
    private fun resolveBrightness(): Int {
        // Manual override
        val manual = tokenStorage.manualBrightness
        if (manual >= 0) {
            Log.d(TAG, "Using manual brightness: $manual%")
            return manual
        }

        // Config event
        val config = tokenStorage.configBrightness
        if (config >= 0) {
            Log.d(TAG, "Using config brightness: $config%")
            return config
        }

        // Default: 100%
        Log.d(TAG, "Using default brightness: 100%")
        return 100
    }

    /**
     * Resolve font size with priority:
     * 1. Manual override
     * 2. Config event
     * 3. Default (48sp)
     */
    private fun resolveFontSize(): Int {
        // Manual override
        val manual = tokenStorage.manualFontSize
        if (manual > 0) {
            Log.d(TAG, "Using manual font size: ${manual}sp")
            return manual
        }

        // Config event
        val config = tokenStorage.configFontSize
        if (config > 0) {
            Log.d(TAG, "Using config font size: ${config}sp")
            return config
        }

        // Default: 48sp
        Log.d(TAG, "Using default font size: ${AppSettings.DEFAULT_FONT_SIZE}sp")
        return AppSettings.DEFAULT_FONT_SIZE
    }

    /**
     * Resolve message area size with priority:
     * 1. Manual override
     * 2. Config event
     * 3. Default (60%)
     */
    private fun resolveMessageSize(): Int {
        // Manual override
        val manual = tokenStorage.manualMessageSize
        if (manual > 0) {
            Log.d(TAG, "Using manual message size: $manual%")
            return manual
        }

        // Config event
        val config = tokenStorage.configMessageSize
        if (config > 0) {
            Log.d(TAG, "Using config message size: $config%")
            return config
        }

        // Default: 60%
        Log.d(TAG, "Using default message size: ${AppSettings.DEFAULT_MESSAGE_AREA_PERCENT}%")
        return AppSettings.DEFAULT_MESSAGE_AREA_PERCENT
    }

    /**
     * Parse a time string in HH:mm format.
     *
     * @param timeStr Time string like "21:00" or "7:30"
     * @return LocalTime or null if parsing fails
     */
    private fun parseTime(timeStr: String): LocalTime? {
        return try {
            LocalTime.parse(timeStr, TIME_FORMATTER)
        } catch (e: Exception) {
            // Try parsing without leading zero (e.g., "7:30")
            try {
                val parts = timeStr.split(":")
                if (parts.size == 2) {
                    val hour = parts[0].toInt()
                    val minute = parts[1].toInt()
                    LocalTime.of(hour, minute)
                } else {
                    null
                }
            } catch (e2: Exception) {
                Log.w(TAG, "Failed to parse time: $timeStr", e2)
                null
            }
        }
    }

    /**
     * Update settings from a manual override change.
     * Call this from admin mode when user changes a setting.
     */
    suspend fun onManualSettingsChanged() {
        refreshSettings()
    }

    /**
     * Clear the solar time cache (call at midnight for fresh data).
     */
    fun clearSolarCache() {
        sunriseSunsetApi.clearCache()
    }
}
