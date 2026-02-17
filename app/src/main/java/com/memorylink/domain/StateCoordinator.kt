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
            calendarRepository.observeConfigEvents().distinctUntilChanged().collect { configEvents
                ->
                if (configEvents.isNotEmpty()) {
                    Log.d(TAG, "Processing ${configEvents.size} config events")
                    val appliedCount = parseConfigEventUseCase(configEvents)
                    Log.d(TAG, "Applied $appliedCount config settings")

                    // Trigger settings refresh - the settings flow observation handles
                    // state update, alarm rescheduling, etc.
                    settingsRepository.refreshSettings()
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
