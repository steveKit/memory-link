package com.memorylink

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.sync.CalendarSyncWorker
import com.memorylink.domain.StateTransitionScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Main Application class for MemoryLink.
 *
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection. Implements WorkManager
 * Configuration for Hilt-aware Workers.
 *
 * Initializes:
 * - [StateTransitionScheduler] for precise wake/sleep/minute tick alarms
 * - [CalendarSyncWorker] for calendar sync (only during awake period)
 */
@HiltAndroidApp
class MemoryLinkApp : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "MemoryLinkApp"
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var tokenStorage: TokenStorage

    @Inject lateinit var stateTransitionScheduler: StateTransitionScheduler

    override fun onCreate() {
        super.onCreate()

        // Initialize the state transition scheduler (handles wake/sleep alarms)
        Log.d(TAG, "Initializing StateTransitionScheduler")
        stateTransitionScheduler.initialize()

        // Schedule calendar sync only if user is signed in AND we're in awake period
        // During sleep, the scheduler will cancel sync; at wake, it will restart
        if (tokenStorage.isSignedIn && !tokenStorage.selectedCalendarId.isNullOrBlank()) {
            if (stateTransitionScheduler.isAwakePeriod()) {
                Log.d(TAG, "In awake period - scheduling calendar sync")
                CalendarSyncWorker.schedulePeriodicSync(this)
                CalendarSyncWorker.triggerImmediateSync(this)
            } else {
                Log.d(TAG, "In sleep period - skipping calendar sync until wake")
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
