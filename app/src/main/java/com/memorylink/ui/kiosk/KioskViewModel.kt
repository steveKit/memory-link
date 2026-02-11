package com.memorylink.ui.kiosk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorylink.domain.StateCoordinator
import com.memorylink.domain.model.DisplayState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Kiosk (main display) screen.
 *
 * Observes the StateCoordinator's display state flow and exposes it to the UI.
 * The state updates every minute via TimeProvider and whenever events/settings change.
 *
 * This ViewModel has minimal logic - it delegates all state decisions to the domain layer.
 */
@HiltViewModel
class KioskViewModel @Inject constructor(
    private val stateCoordinator: StateCoordinator
) : ViewModel() {

    /**
     * The current display state to render.
     *
     * This StateFlow:
     * - Emits every minute (for clock updates)
     * - Emits when calendar events change
     * - Emits when settings change (sleep/wake times, format)
     *
     * Uses WhileSubscribed(5000) to keep the flow active for 5 seconds after the last
     * subscriber disconnects, preventing unnecessary restarts during configuration changes.
     */
    val displayState: StateFlow<DisplayState> = stateCoordinator.observeDisplayState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = stateCoordinator.displayState.value
        )

    /**
     * Force an immediate state refresh.
     * Called when the app returns to foreground or after config changes.
     */
    fun refresh() {
        stateCoordinator.refreshState()
    }
}
