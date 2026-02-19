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
 * TokenStorage. Settings are unified - both admin panel and [CONFIG] events write to the same
 * fields. Last write wins.
 *
 * Per .clinerules/10-project-meta.md:
 * - Config events are parsed immediately when cached
 * - Settings persist until overwritten (either via [CONFIG] event or admin panel)
 * - Invalid syntax is logged but ignored
 *
 * Flow:
 * 1. CalendarSyncWorker syncs events → triggers this use case
 * 2. This use case parses [CONFIG] events (chronologically by startTime)
 * 3. Settings are stored in TokenStorage (unified fields)
 * 4. SettingsRepository refreshes to load updated settings
 * 5. Successfully processed event IDs are returned for deletion
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
     * Result of processing config events.
     *
     * @param appliedCount Number of config settings successfully applied
     * @param processedEventIds IDs of events that were successfully processed (for deletion)
     */
    data class ProcessResult(val appliedCount: Int, val processedEventIds: List<String>)

    /**
     * Process a list of config events and update settings.
     *
     * Events are processed chronologically (oldest first) to ensure proper ordering when multiple
     * config events modify the same setting.
     *
     * @param configEvents List of calendar events that are config events
     * @return ProcessResult with count and IDs of successfully processed events
     */
    suspend operator fun invoke(configEvents: List<CalendarEvent>): ProcessResult {
        if (configEvents.isEmpty()) {
            Log.d(TAG, "No config events to process")
            return ProcessResult(0, emptyList())
        }

        Log.d(TAG, "Processing ${configEvents.size} config events")

        // Sort by startTime ascending (chronological order - oldest first)
        val sortedEvents = configEvents.sortedBy { it.startTime }

        val processedEventIds = mutableListOf<String>()
        var appliedCount = 0

        // Parse and apply each config event in chronological order
        for (event in sortedEvents) {
            val result = ConfigParser.parse(event.title)

            if (result is Invalid) {
                Log.w(TAG, "Invalid config: '${event.title}' - ${result.reason}")
                // Don't add to processedEventIds - invalid configs stay in cache
                // (they won't be retried since parsing will always fail)
                continue
            }

            applyConfig(result)
            processedEventIds.add(event.id)
            appliedCount++
            Log.d(TAG, "Applied config: ${event.title} (id: ${event.id})")
        }

        // Refresh settings after processing all configs
        if (appliedCount > 0) {
            settingsRepository.refreshSettings()
        }

        Log.d(
                TAG,
                "Applied $appliedCount config settings, ${processedEventIds.size} events to delete"
        )
        return ProcessResult(appliedCount, processedEventIds)
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

    /** Apply sleep time configuration to storage. */
    private fun applySleepConfig(config: SleepConfig) {
        tokenStorage.sleepTime = config.time.format(TIME_FORMATTER)
        Log.d(TAG, "Set sleep time: ${config.time}")
    }

    /** Apply wake time configuration to storage. */
    private fun applyWakeConfig(config: WakeConfig) {
        tokenStorage.wakeTime = config.time.format(TIME_FORMATTER)
        Log.d(TAG, "Set wake time: ${config.time}")
    }

    /** Apply brightness configuration. */
    private fun applyBrightnessConfig(config: BrightnessConfig) {
        tokenStorage.brightness = config.percent
        Log.d(TAG, "Set brightness: ${config.percent}%")
    }

    /** Apply time format configuration. */
    private fun applyTimeFormatConfig(config: TimeFormatConfig) {
        tokenStorage.use24HourFormat = config.use24Hour
        Log.d(TAG, "Set time format: ${if (config.use24Hour) "24h" else "12h"}")
    }

    /** Clear all settings. Call this when user wants to reset to defaults. */
    suspend fun clearAllSettings() {
        tokenStorage.clearSettings()
        settingsRepository.refreshSettings()
        Log.d(TAG, "Cleared all settings")
    }
}
