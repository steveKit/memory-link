package com.memorylink.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.memorylink.domain.StateCoordinator
import com.memorylink.domain.StateTransitionScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BroadcastReceiver for handling wake/sleep state transition alarms.
 *
 * Receives broadcasts from AlarmManager and delegates to:
 * - StateCoordinator for state refresh
 * - StateTransitionScheduler for rescheduling
 * - KioskForegroundService for starting/stopping sync
 *
 * Actions handled:
 * - ACTION_WAKE: Transition to awake state, start foreground service
 * - ACTION_SLEEP: Transition to sleep state, stop foreground service
 */
@AndroidEntryPoint
class StateTransitionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "StateTransitionReceiver"
    }

    @Inject lateinit var stateCoordinator: StateCoordinator

    @Inject lateinit var scheduler: StateTransitionScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received action: $action")

        when (action) {
            StateTransitionScheduler.ACTION_WAKE -> handleWake(context)
            StateTransitionScheduler.ACTION_SLEEP -> handleSleep(context)
            else -> Log.w(TAG, "Unknown action: $action")
        }
    }

    /**
     * Handle wake alarm:
     * 1. Refresh display state (transition to awake)
     * 2. Start KioskForegroundService for sync and state refresh
     * 3. Reschedule wake alarm for tomorrow
     */
    private fun handleWake(context: Context) {
        Log.d(TAG, "Handling wake transition")

        // Refresh state first (will transition to awake state)
        stateCoordinator.refreshState()

        // Start the foreground service for calendar sync and minute ticks
        KioskForegroundService.start(context)

        // Notify scheduler to reschedule wake alarm for tomorrow
        scheduler.onWakeAlarmFired()
    }

    /**
     * Handle sleep alarm:
     * 1. Refresh display state (transition to sleep)
     * 2. Stop KioskForegroundService (no need for sync during sleep)
     * 3. Reschedule sleep alarm for tomorrow
     */
    private fun handleSleep(context: Context) {
        Log.d(TAG, "Handling sleep transition")

        // Refresh state first (will transition to sleep state)
        stateCoordinator.refreshState()

        // Stop the foreground service during sleep - no need for frequent sync
        KioskForegroundService.stop(context)

        // Notify scheduler to reschedule sleep alarm for tomorrow
        scheduler.onSleepAlarmFired()
    }
}
