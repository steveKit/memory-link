package com.memorylink.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.memorylink.data.sync.CalendarSyncWorker
import com.memorylink.domain.StateCoordinator
import com.memorylink.domain.StateTransitionScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BroadcastReceiver for handling state transition alarms.
 *
 * Receives broadcasts from AlarmManager and delegates to:
 * - StateCoordinator for state refresh
 * - StateTransitionScheduler for rescheduling
 * - CalendarSyncWorker for wake-time sync
 *
 * Actions handled:
 * - ACTION_WAKE: Transition to awake state, trigger calendar sync
 * - ACTION_SLEEP: Transition to sleep state, cancel sync
 * - ACTION_MINUTE_TICK: Update clock display
 */
@AndroidEntryPoint
class StateTransitionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "StateTransitionReceiver"
    }

    @Inject
    lateinit var stateCoordinator: StateCoordinator

    @Inject
    lateinit var scheduler: StateTransitionScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received action: $action")

        when (action) {
            StateTransitionScheduler.ACTION_WAKE -> handleWake(context)
            StateTransitionScheduler.ACTION_SLEEP -> handleSleep(context)
            StateTransitionScheduler.ACTION_MINUTE_TICK -> handleMinuteTick()
            else -> Log.w(TAG, "Unknown action: $action")
        }
    }

    /**
     * Handle wake alarm:
     * 1. Refresh display state (transition to awake)
     * 2. Trigger immediate calendar sync (fresh data for the day)
     * 3. Start minute ticks for clock updates
     */
    private fun handleWake(context: Context) {
        Log.d(TAG, "Handling wake transition")
        
        // Refresh state first (will transition to awake state)
        stateCoordinator.refreshState()
        
        // Trigger immediate calendar sync for fresh data
        CalendarSyncWorker.triggerImmediateSync(context)
        
        // Schedule periodic sync now that we're awake
        CalendarSyncWorker.schedulePeriodicSync(context)
        
        // Notify scheduler to start minute ticks and reschedule wake alarm
        scheduler.onWakeAlarmFired()
    }

    /**
     * Handle sleep alarm:
     * 1. Refresh display state (transition to sleep)
     * 2. Cancel periodic sync (no point syncing during sleep)
     * 3. Stop minute ticks (battery saving)
     */
    private fun handleSleep(context: Context) {
        Log.d(TAG, "Handling sleep transition")
        
        // Refresh state first (will transition to sleep state)
        stateCoordinator.refreshState()
        
        // Cancel periodic sync during sleep - no need to check calendar
        CalendarSyncWorker.cancelAllSync(context)
        
        // Notify scheduler to stop minute ticks and reschedule sleep alarm
        scheduler.onSleepAlarmFired()
    }

    /**
     * Handle minute tick:
     * 1. Refresh display state (update clock, check event times)
     * 2. Reschedule next minute tick
     */
    private fun handleMinuteTick() {
        Log.d(TAG, "Handling minute tick")
        
        // Refresh state (updates clock time and re-evaluates events)
        stateCoordinator.refreshState()
        
        // Notify scheduler to reschedule next tick
        scheduler.onMinuteTickFired()
    }
}
