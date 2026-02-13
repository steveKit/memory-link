package com.memorylink.domain.usecase

import android.util.Log
import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.repository.SettingsRepository
import com.memorylink.domain.model.CalendarEvent
import com.memorylink.domain.model.ConfigResult
import com.memorylink.domain.model.ConfigResult.*
import com.memorylink.util.ConfigParser
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Use case for parsing [CONFIG] calendar events and applying settings.
 *
 * Processes config events from the calendar cache and stores the resulting settings in
 * TokenStorage. Settings are then resolved by SettingsRepository with proper priority handling.
 *
 * Per .clinerules/10-project-meta.md:
 * - Config events are parsed immediately when cached
 * - Settings persist until overwritten by another [CONFIG] event
 * - Invalid syntax is logged but ignored
 *
 * Flow:
 * 1. CalendarSyncWorker syncs events → triggers this use case
 * 2. This use case parses [CONFIG] events
 * 3. Settings are stored in TokenStorage
 * 4. SettingsRepository refreshes to combine with manual overrides
 */
class ParseConfigEventUseCase
@Inject
constructor(
        private val tokenStorage: TokenStorage,
        private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "ParseConfigEventUseCase"
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }

    /**
     * Process a list of config events and update settings.
     *
     * @param configEvents List of calendar events that are config events
     * @return Number of config settings successfully applied
     */
    suspend operator fun invoke(configEvents: List<CalendarEvent>): Int {
        if (configEvents.isEmpty()) {
            Log.d(TAG, "No config events to process")
            return 0
        }

        Log.d(TAG, "Processing ${configEvents.size} config events")

        var appliedCount = 0

        // Parse and apply each config event
        for (event in configEvents) {
            val result = ConfigParser.parse(event.title)

            if (result is Invalid) {
                Log.w(TAG, "Invalid config: '${event.title}' - ${result.reason}")
                continue
            }

            applyConfig(result)
            appliedCount++
            Log.d(TAG, "Applied config: ${event.title}")
        }

        // Refresh settings after processing all configs
        if (appliedCount > 0) {
            settingsRepository.refreshSettings()
        }

        Log.d(TAG, "Applied $appliedCount config settings")
        return appliedCount
    }

    /**
     * Process config events from their titles only (convenience method).
     *
     * @param titles List of event titles to parse
     * @return Number of config settings successfully applied
     */
    suspend fun processFromTitles(titles: List<String>): Int {
        if (titles.isEmpty()) {
            return 0
        }

        Log.d(TAG, "Processing ${titles.size} config titles")

        var appliedCount = 0

        for (title in titles) {
            if (!ConfigParser.isConfigEvent(title)) {
                continue
            }

            val result = ConfigParser.parse(title)

            if (result is Invalid) {
                Log.w(TAG, "Invalid config: '$title' - ${result.reason}")
                continue
            }

            applyConfig(result)
            appliedCount++
        }

        if (appliedCount > 0) {
            settingsRepository.refreshSettings()
        }

        return appliedCount
    }

    /**
     * Apply a single parsed config result to storage.
     *
     * @param config The parsed ConfigResult to apply
     */
    private fun applyConfig(config: ConfigResult) {
        when (config) {
            is SleepConfig -> applySleepConfig(config)
            is WakeConfig -> applyWakeConfig(config)
            is BrightnessConfig -> applyBrightnessConfig(config)
            is TimeFormatConfig -> applyTimeFormatConfig(config)
            is Invalid -> {
                /* Already handled */
            }
        }
    }

    /** Apply sleep time configuration. */
    private fun applySleepConfig(config: SleepConfig) {
        when (config) {
            is SleepConfig.StaticTime -> {
                // Clear any solar reference, set static time
                tokenStorage.configSleepSolarRef = null
                tokenStorage.configSleepSolarOffset = 0
                tokenStorage.configSleepTime = config.time.format(TIME_FORMATTER)
                Log.d(TAG, "Set sleep time: ${config.time}")
            }
            is SleepConfig.DynamicTime -> {
                // Clear static time, set solar reference
                tokenStorage.configSleepTime = null
                tokenStorage.configSleepSolarRef = config.reference.name
                tokenStorage.configSleepSolarOffset = config.offsetMinutes
                Log.d(
                        TAG,
                        "Set sleep time: ${config.reference}${formatOffset(config.offsetMinutes)}"
                )
            }
        }
    }

    /** Apply wake time configuration. */
    private fun applyWakeConfig(config: WakeConfig) {
        when (config) {
            is WakeConfig.StaticTime -> {
                // Clear any solar reference, set static time
                tokenStorage.configWakeSolarRef = null
                tokenStorage.configWakeSolarOffset = 0
                tokenStorage.configWakeTime = config.time.format(TIME_FORMATTER)
                Log.d(TAG, "Set wake time: ${config.time}")
            }
            is WakeConfig.DynamicTime -> {
                // Clear static time, set solar reference
                tokenStorage.configWakeTime = null
                tokenStorage.configWakeSolarRef = config.reference.name
                tokenStorage.configWakeSolarOffset = config.offsetMinutes
                Log.d(
                        TAG,
                        "Set wake time: ${config.reference}${formatOffset(config.offsetMinutes)}"
                )
            }
        }
    }

    /** Apply brightness configuration. */
    private fun applyBrightnessConfig(config: BrightnessConfig) {
        tokenStorage.configBrightness = config.percent
        Log.d(TAG, "Set brightness: ${config.percent}%")
    }

    /** Apply time format configuration. */
    private fun applyTimeFormatConfig(config: TimeFormatConfig) {
        tokenStorage.configUse24HourFormat = config.use24Hour
        Log.d(TAG, "Set time format: ${if (config.use24Hour) "24h" else "12h"}")
    }

    /** Format offset minutes for logging (e.g., +30, -15, or empty for 0). */
    private fun formatOffset(minutes: Int): String {
        return when {
            minutes > 0 -> "+$minutes"
            minutes < 0 -> "$minutes"
            else -> ""
        }
    }

    /** Clear all config event settings. Call this when user wants to reset to defaults. */
    suspend fun clearAllConfigs() {
        tokenStorage.clearConfigSettings()
        settingsRepository.refreshSettings()
        Log.d(TAG, "Cleared all config settings")
    }
}
