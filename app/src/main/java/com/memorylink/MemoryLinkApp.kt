package com.memorylink

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.sync.CalendarSyncWorker
import com.memorylink.domain.StateCoordinator
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

    /**
     * StateCoordinator is eagerly initialized to ensure config event observation starts immediately
     * on app startup, not just when KioskViewModel is created.
     */
    @Inject lateinit var stateCoordinator: StateCoordinator

    override fun onCreate() {
        super.onCreate()

        // Initialize the state transition scheduler (handles wake/sleep alarms)
        Log.d(TAG, "Initializing StateTransitionScheduler")
        stateTransitionScheduler.initialize()

        // Schedule daily backup sync if user is signed in
        // Primary 15-minute sync is handled by KioskForegroundService
        // Daily backup ensures sync even if foreground service is killed
        if (tokenStorage.isSignedIn && !tokenStorage.selectedCalendarId.isNullOrBlank()) {
            Log.d(TAG, "Scheduling daily backup sync")
            CalendarSyncWorker.scheduleDailyBackupSync(this)

            // Trigger immediate sync if in awake period
            if (stateTransitionScheduler.isAwakePeriod()) {
                Log.d(TAG, "In awake period - triggering immediate sync")
                CalendarSyncWorker.triggerImmediateSync(this)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
