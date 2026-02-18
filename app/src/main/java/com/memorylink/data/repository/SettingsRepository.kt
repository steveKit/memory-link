package com.memorylink.data.repository

import android.util.Log
import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.remote.SunriseSunsetApi
import com.memorylink.domain.model.AppSettings
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for app settings.
 *
 * Settings are unified - both admin panel and [CONFIG] calendar events write to the same storage.
 * Last write wins, no priority system.
 *
 * For dynamic times (SUNRISE/SUNSET), the SunriseSunsetApi is used to resolve the actual time, with
 * fallbacks to defaults.
 *
 * See .clinerules/10-project-meta.md for config documentation.
 */
@Singleton
class SettingsRepository
@Inject
constructor(
        private val tokenStorage: TokenStorage,
        private val sunriseSunsetApi: SunriseSunsetApi
) {
    companion object {
        private const val TAG = "SettingsRepository"
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }

    /** Current resolved settings. Updated when settings change. */
    private val _settings = MutableStateFlow(AppSettings())
    val settings: Flow<AppSettings> = _settings.asStateFlow()

    /** Get current settings (non-Flow version for synchronous access). */
    val currentSettings: AppSettings
        get() = _settings.value

    /**
     * Refresh settings by re-evaluating storage. Call this after settings are changed (via admin
     * panel or [CONFIG] events).
     *
     * @return The updated AppSettings
     */
    suspend fun refreshSettings(): AppSettings {
        val newSettings = buildSettings()
        _settings.value = newSettings
        Log.d(TAG, "Settings refreshed: $newSettings")
        return newSettings
    }

    /** Build AppSettings by reading from storage and resolving solar times. */
    private suspend fun buildSettings(): AppSettings {
        return AppSettings(
                sleepTime = resolveSleepTime(),
                wakeTime = resolveWakeTime(),
                use24HourFormat = tokenStorage.use24HourFormat ?: false,
                brightness = tokenStorage.brightness.takeIf { it >= 0 } ?: 100,
                showYearInDate = tokenStorage.showYear ?: true,
                showEventsDuringSleep = tokenStorage.showEventsDuringSleep ?: false
        )
    }

    /**
     * Resolve sleep time - either from static time or solar calculation. Falls back to default
     * (SUNSET+30 or 21:00) if nothing is set.
     */
    private suspend fun resolveSleepTime(): LocalTime {
        // Check for static time
        tokenStorage.sleepTime?.let { timeStr ->
            parseTime(timeStr)?.let { time ->
                Log.d(TAG, "Using static sleep time: $time")
                return time
            }
        }

        // Check for solar time
        tokenStorage.sleepSolarRef?.let { solarRef ->
            val offset = tokenStorage.sleepSolarOffset
            val resolvedTime = resolveSolarTime(solarRef, offset, AppSettings.DEFAULT_SLEEP_TIME)
            Log.d(TAG, "Using solar sleep time: $solarRef${formatOffset(offset)} -> $resolvedTime")
            return resolvedTime
        }

        // Default: SUNSET+30 (fallback to 21:00)
        val defaultSolar = resolveSolarTime("SUNSET", 30, AppSettings.DEFAULT_SLEEP_TIME)
        Log.d(TAG, "Using default sleep time: SUNSET+30 -> $defaultSolar")
        return defaultSolar
    }

    /**
     * Resolve wake time - either from static time or solar calculation. Falls back to default
     * (SUNRISE or 06:00) if nothing is set.
     */
    private suspend fun resolveWakeTime(): LocalTime {
        // Check for static time
        tokenStorage.wakeTime?.let { timeStr ->
            parseTime(timeStr)?.let { time ->
                Log.d(TAG, "Using static wake time: $time")
                return time
            }
        }

        // Check for solar time
        tokenStorage.wakeSolarRef?.let { solarRef ->
            val offset = tokenStorage.wakeSolarOffset
            val resolvedTime = resolveSolarTime(solarRef, offset, AppSettings.DEFAULT_WAKE_TIME)
            Log.d(TAG, "Using solar wake time: $solarRef${formatOffset(offset)} -> $resolvedTime")
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
        val baseTime =
                when (solarRef.uppercase()) {
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

    /** Notify that settings have changed. Call after admin panel or [CONFIG] event changes. */
    suspend fun onSettingsChanged() {
        refreshSettings()
    }

    /** Clear the solar time cache (call at midnight for fresh data). */
    fun clearSolarCache() {
        sunriseSunsetApi.clearCache()
    }

    /** Format offset for logging (e.g., +30, -15, or empty for 0). */
    private fun formatOffset(offset: Int): String {
        return when {
            offset > 0 -> "+$offset"
            offset < 0 -> "$offset"
            else -> ""
        }
    }
}
