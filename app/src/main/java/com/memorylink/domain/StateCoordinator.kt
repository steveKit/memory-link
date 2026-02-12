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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Central coordinator for the display state machine.
 *
 * Combines:
 * - Time ticks (every minute)
 * - Calendar events (from repository - observed reactively from Room)
 * - App settings (sleep/wake times, format)
 *
 * Produces a single [DisplayState] flow that the UI observes.
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

    /** Current display state. UI observes this to render the appropriate screen. */
    private val _displayState = MutableStateFlow<DisplayState>(createInitialState())
    val displayState: StateFlow<DisplayState> = _displayState.asStateFlow()

    /** Current app settings. Updated by config parser or admin settings. */
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * Current calendar events for today. Updated reactively from Room database via
     * CalendarRepository.
     */
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    init {
        // Start observing Room database for today's events
        // This creates a reactive connection - any changes in Room will flow here
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

        Log.d(TAG, "StateCoordinator initialized - observing repository Flows")
    }

    /**
     * Get a flow of display states that updates every minute and when events/settings change.
     *
     * This combines:
     * 1. Minute ticks from TimeProvider
     * 2. Current events
     * 3. Current settings
     *
     * @return Flow emitting the current DisplayState
     */
    fun observeDisplayState(): Flow<DisplayState> {
        return combine(timeProvider.minuteTicks(), _events, _settings) { now, events, settings ->
            determineDisplayStateUseCase(now, events, settings)
        }
    }

    /**
     * Update the current display state. Called by the ViewModel when observing the combined flow.
     */
    fun updateDisplayState(state: DisplayState) {
        _displayState.value = state
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

    /** Force a state refresh. Useful at wake/sleep boundaries or after config changes. */
    fun refreshState() {
        val now = timeProvider.now()
        val state = determineDisplayStateUseCase(now, _events.value, _settings.value)
        _displayState.value = state
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
