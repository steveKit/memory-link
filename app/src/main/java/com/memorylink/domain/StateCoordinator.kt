package com.memorylink.domain

import com.memorylink.domain.model.AppSettings
import com.memorylink.domain.model.CalendarEvent
import com.memorylink.domain.model.DisplayState
import com.memorylink.domain.usecase.DetermineDisplayStateUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central coordinator for the display state machine.
 *
 * Combines:
 * - Time ticks (every minute)
 * - Calendar events (from repository)
 * - App settings (sleep/wake times, format)
 *
 * Produces a single [DisplayState] flow that the UI observes.
 *
 * See .clinerules/40-state-machine.md for state transitions.
 */
@Singleton
class StateCoordinator @Inject constructor(
    private val timeProvider: TimeProvider,
    private val determineDisplayStateUseCase: DetermineDisplayStateUseCase
) {

    /**
     * Current display state.
     * UI observes this to render the appropriate screen.
     */
    private val _displayState = MutableStateFlow<DisplayState>(createInitialState())
    val displayState: StateFlow<DisplayState> = _displayState.asStateFlow()

    /**
     * Current app settings.
     * Updated by config parser or admin settings.
     */
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * Current calendar events for today.
     * Updated by calendar sync.
     */
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

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
        return combine(
            timeProvider.minuteTicks(),
            _events,
            _settings
        ) { now, events, settings ->
            determineDisplayStateUseCase(now, events, settings)
        }
    }

    /**
     * Update the current display state.
     * Called by the ViewModel when observing the combined flow.
     */
    fun updateDisplayState(state: DisplayState) {
        _displayState.value = state
    }

    /**
     * Update calendar events.
     * Called by the calendar sync worker after fetching new events.
     */
    fun updateEvents(newEvents: List<CalendarEvent>) {
        _events.value = newEvents
        // Re-evaluate state immediately when events change
        refreshState()
    }

    /**
     * Update app settings.
     * Called when config events are parsed or admin changes settings.
     */
    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        // Re-evaluate state immediately when settings change
        refreshState()
    }

    /**
     * Force a state refresh.
     * Useful at wake/sleep boundaries or after config changes.
     */
    fun refreshState() {
        val now = timeProvider.now()
        val state = determineDisplayStateUseCase(now, _events.value, _settings.value)
        _displayState.value = state
    }

    /**
     * Create the initial state on startup.
     * Uses current time and default settings until data is loaded.
     */
    private fun createInitialState(): DisplayState {
        val now = timeProvider.now()
        return determineDisplayStateUseCase(now, emptyList(), AppSettings())
    }
}
