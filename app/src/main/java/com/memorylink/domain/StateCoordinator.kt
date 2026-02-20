package com.memorylink.domain

import android.util.Log
import com.memorylink.data.repository.CalendarRepository
import com.memorylink.data.repository.SettingsRepository
import com.memorylink.di.ApplicationScope
import com.memorylink.domain.model.AppSettings
import com.memorylink.domain.model.CalendarEvent
import com.memorylink.domain.model.DisplayState
import com.memorylink.domain.usecase.DetermineDisplayStateUseCase
import com.memorylink.domain.usecase.ParseConfigEventUseCase
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Central coordinator for display state. Single source of truth via StateFlow.
 *
 * Observes Room for events, processes [CONFIG] events, responds to alarm triggers.
 */
@Singleton
class StateCoordinator
@Inject
constructor(
        private val timeProvider: TimeProvider,
        private val determineDisplayStateUseCase: DetermineDisplayStateUseCase,
        private val calendarRepository: CalendarRepository,
        private val settingsRepository: SettingsRepository,
        private val parseConfigEventUseCase: ParseConfigEventUseCase,
        private val scheduler: Lazy<StateTransitionScheduler>,
        @ApplicationScope private val applicationScope: CoroutineScope
) {

    companion object {
        private const val TAG = "StateCoordinator"

        /** Debounce window for state refresh to avoid redundant calculations. */
        private const val REFRESH_DEBOUNCE_MS = 50L
    }

    /** Job for debounced refresh - cancelled and restarted on each refresh request. */
    private var refreshJob: kotlinx.coroutines.Job? = null

    /** Counter for failed config event deletions (for admin visibility if needed). */
    private val _pendingConfigDeletions = MutableStateFlow(0)
    val pendingConfigDeletions: StateFlow<Int> = _pendingConfigDeletions.asStateFlow()

    /** Current display state. UI observes this single StateFlow to render. */
    private val _displayState = MutableStateFlow<DisplayState>(createInitialState())
    val displayState: StateFlow<DisplayState> = _displayState.asStateFlow()

    /** Current app settings. Updated by config parser or admin settings. */
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Current calendar events for today. Updated reactively from Room database. */
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    init {
        // Start observing Room database for all upcoming events (2-week lookahead)
        applicationScope.launch {
            calendarRepository.observeUpcomingEvents().distinctUntilChanged().collect { events ->
                Log.d(TAG, "Received ${events.size} upcoming events from Room")
                _events.value = events
                refreshState()
            }
        }

        // Start observing config events and process them
        applicationScope.launch {
            Log.d(TAG, "Starting config events observation...")
            calendarRepository.observeConfigEvents().distinctUntilChanged().collect { configEvents
                ->
                Log.d(TAG, "Config events flow emitted: ${configEvents.size} event(s)")

                if (configEvents.isNotEmpty()) {
                    // Log each config event for debugging
                    configEvents.forEach { event ->
                        Log.d(TAG, "  - Config event: '${event.title}' (id: ${event.id})")
                    }

                    Log.d(TAG, "Processing ${configEvents.size} config events")
                    val result = parseConfigEventUseCase(configEvents)
                    Log.d(TAG, "Applied ${result.appliedCount} config settings")

                    // Delete successfully processed config events from Google Calendar and cache
                    if (result.processedEventIds.isNotEmpty()) {
                        Log.d(TAG, "Deleting ${result.processedEventIds.size} processed events...")
                        deleteProcessedConfigEvents(result.processedEventIds)
                    }

                    // Trigger settings refresh - the settings flow observation handles
                    // state update, alarm rescheduling, etc.
                    Log.d(TAG, "Triggering settings refresh after config processing")
                    settingsRepository.refreshSettings()
                } else {
                    Log.d(TAG, "No config events to process")
                }
            }
        }

        // Observe settings changes and notify scheduler to reschedule alarms
        applicationScope.launch {
            settingsRepository.settings.distinctUntilChanged().collect { newSettings ->
                Log.d(TAG, "Settings updated: $newSettings")
                val previousSettings = _settings.value
                _settings.value = newSettings

                // If sleep/wake times changed, reschedule alarms
                if (previousSettings.sleepTime != newSettings.sleepTime ||
                                previousSettings.wakeTime != newSettings.wakeTime
                ) {
                    Log.d(TAG, "Sleep/wake times changed, rescheduling alarms")
                    scheduler.get().onSettingsChanged(newSettings)
                }

                refreshState()
            }
        }

        Log.d(TAG, "StateCoordinator initialized")
    }

    /**
     * Update calendar events manually. Primarily used for testing or forced updates. Normal updates
     * flow through Room observation.
     */
    fun updateEvents(newEvents: List<CalendarEvent>) {
        _events.value = newEvents
        refreshState()
    }

    /** Update app settings. Called when config events are parsed or admin changes settings. */
    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        refreshState()
    }

    /**
     * Force a state refresh with debouncing.
     *
     * Called by [StateTransitionReceiver] when:
     * - Wake alarm fires (transition to awake)
     * - Sleep alarm fires (transition to sleep)
     * - Minute tick fires (update clock, check events)
     *
     * Also called when events or settings change.
     *
     * Debouncing prevents redundant state calculations when multiple triggers fire in quick
     * succession (e.g., settings + events both change).
     */
    fun refreshState() {
        refreshJob?.cancel()
        refreshJob =
                applicationScope.launch {
                    kotlinx.coroutines.delay(REFRESH_DEBOUNCE_MS)
                    performStateRefresh()
                }
    }

    /**
     * Immediate state refresh without debouncing. Use for critical transitions where delay is
     * unacceptable.
     */
    fun refreshStateImmediate() {
        refreshJob?.cancel()
        performStateRefresh()
    }

    /** Internal: performs the actual state calculation and update. */
    private fun performStateRefresh() {
        val now = timeProvider.now()
        val state = determineDisplayStateUseCase(now, _events.value, _settings.value)
        val previousState = _displayState.value
        _displayState.value = state

        if (previousState::class != state::class) {
            Log.d(
                    TAG,
                    "State transition: ${previousState::class.simpleName} -> ${state::class.simpleName}"
            )
        }
    }

    /**
     * Create the initial state on startup. Uses current time and default settings until data is
     * loaded.
     */
    private fun createInitialState(): DisplayState {
        val now = timeProvider.now()
        return determineDisplayStateUseCase(now, emptyList(), AppSettings())
    }

    /**
     * Delete processed config events from Google Calendar and local cache.
     *
     * This signals to remote caregivers that the config has been consumed and settings updated.
     * Deletions are best-effort: failures are logged but don't block other operations. Failed
     * deletions are tracked in [pendingConfigDeletions] for admin visibility and will be retried on
     * the next sync cycle.
     *
     * @param eventIds List of event IDs to delete
     */
    private fun deleteProcessedConfigEvents(eventIds: List<String>) {
        // Track pending deletions for visibility
        _pendingConfigDeletions.value += eventIds.size

        applicationScope.launch {
            var successCount = 0
            var failCount = 0

            for (eventId in eventIds) {
                val deleted = calendarRepository.deleteConfigEvent(eventId)
                if (deleted) {
                    successCount++
                } else {
                    failCount++
                }
            }

            // Update pending count - subtract successful deletions
            _pendingConfigDeletions.value =
                    (_pendingConfigDeletions.value - successCount).coerceAtLeast(0)

            if (successCount > 0) {
                Log.d(TAG, "Deleted $successCount config events from calendar")
            }
            if (failCount > 0) {
                Log.w(
                        TAG,
                        "Failed to delete $failCount config events " +
                                "(pending: ${_pendingConfigDeletions.value}, will retry on next sync)"
                )
            }
        }
    }
}
