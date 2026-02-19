package com.memorylink.data.repository

import android.util.Log
import com.memorylink.data.auth.TokenStorage
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
 */
@Singleton
class SettingsRepository
@Inject
constructor(
        private val tokenStorage: TokenStorage
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

    /** Build AppSettings by reading from storage. */
    private fun buildSettings(): AppSettings {
        return AppSettings(
                sleepTime = resolveSleepTime(),
                wakeTime = resolveWakeTime(),
                use24HourFormat = tokenStorage.use24HourFormat ?: false,
                brightness = tokenStorage.brightness.takeIf { it >= 0 } ?: 100,
                showYearInDate = tokenStorage.showYear ?: false,
                showEventsDuringSleep = tokenStorage.showEventsDuringSleep ?: false
        )
    }

    /**
     * Resolve sleep time from storage.
     * Falls back to default if not set.
     */
    private fun resolveSleepTime(): LocalTime {
        tokenStorage.sleepTime?.let { timeStr ->
            parseTime(timeStr)?.let { time ->
                Log.d(TAG, "Using stored sleep time: $time")
                return time
            }
        }

        Log.d(TAG, "Using default sleep time: ${AppSettings.DEFAULT_SLEEP_TIME}")
        return AppSettings.DEFAULT_SLEEP_TIME
    }

    /**
     * Resolve wake time from storage.
     * Falls back to default if not set.
     */
    private fun resolveWakeTime(): LocalTime {
        tokenStorage.wakeTime?.let { timeStr ->
            parseTime(timeStr)?.let { time ->
                Log.d(TAG, "Using stored wake time: $time")
                return time
            }
        }

        Log.d(TAG, "Using default wake time: ${AppSettings.DEFAULT_WAKE_TIME}")
        return AppSettings.DEFAULT_WAKE_TIME
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
}
