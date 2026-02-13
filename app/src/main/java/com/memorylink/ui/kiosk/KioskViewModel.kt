package com.memorylink.ui.kiosk

import androidx.lifecycle.ViewModel
import com.memorylink.domain.StateCoordinator
import com.memorylink.domain.model.DisplayState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the Kiosk (main display) screen.
 *
 * Observes the StateCoordinator's display state flow and exposes it to the UI. State updates are
 * triggered by [StateTransitionScheduler] alarms:
 * - At wake/sleep boundaries
 * - Every minute during awake period
 * - When calendar events change
 *
 * This ViewModel has minimal logic - it delegates all state decisions to the domain layer.
 */
@HiltViewModel
class KioskViewModel @Inject constructor(private val stateCoordinator: StateCoordinator) :
        ViewModel() {

    /**
     * The current display state to render.
     *
     * This StateFlow is directly from StateCoordinator and updates when:
     * - Wake/sleep alarms fire (state transitions)
     * - Minute tick alarms fire (clock updates during awake)
     * - Calendar events change (Room database observation)
     * - Settings change
     */
    val displayState: StateFlow<DisplayState> = stateCoordinator.displayState

    /**
     * Force an immediate state refresh. Called when the app returns to foreground or after config
     * changes.
     */
    fun refresh() {
        stateCoordinator.refreshState()
    }
}
