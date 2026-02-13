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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Central coordinator for the display state machine.
 *
 * Manages:
 * - Display state (single source of truth via StateFlow)
 * - Calendar events (observed reactively from Room)
 * - App settings (sleep/wake times, format)
 *
 * State updates are triggered by:
 * - [StateTransitionScheduler] alarms (wake/sleep/minute tick)
 * - Calendar sync (Room observation)
 * - Settings changes
 *
 * See .clinerules/40-state-machine.md for state transitions.
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
        @ApplicationScope private val applicationScope: CoroutineScope
) {

    companion object {
        private const val TAG = "StateCoordinator"
    }

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
        // Start observing Room database for today's events
        applicationScope.launch {
            calendarRepository.observeTodaysEvents().distinctUntilChanged().collect { events ->
                Log.d(TAG, "Received ${events.size} events from Room")
                _events.value = events
                refreshState()
            }
        }

        // Start observing config events and process them
        applicationScope.launch {
            calendarRepository.observeConfigEvents().distinctUntilChanged().collect { configEvents
                ->
                if (configEvents.isNotEmpty()) {
                    Log.d(TAG, "Processing ${configEvents.size} config events")
                    val appliedCount = parseConfigEventUseCase(configEvents)
                    Log.d(TAG, "Applied $appliedCount config settings")

                    // Refresh settings after config processing
                    val newSettings = settingsRepository.refreshSettings()
                    updateSettings(newSettings)
                }
            }
        }

        // Observe settings changes
        applicationScope.launch {
            settingsRepository.settings.distinctUntilChanged().collect { settings ->
                Log.d(TAG, "Settings updated: $settings")
                _settings.value = settings
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
     * Force a state refresh.
     *
     * Called by [StateTransitionReceiver] when:
     * - Wake alarm fires (transition to awake)
     * - Sleep alarm fires (transition to sleep)
     * - Minute tick fires (update clock, check events)
     *
     * Also called when events or settings change.
     */
    fun refreshState() {
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
}
