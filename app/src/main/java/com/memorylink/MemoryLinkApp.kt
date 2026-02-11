package com.memorylink

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.sync.CalendarSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Main Application class for MemoryLink. Annotated with @HiltAndroidApp to enable Hilt dependency
 * injection. Implements WorkManager Configuration for Hilt-aware Workers.
 */
@HiltAndroidApp
class MemoryLinkApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var tokenStorage: TokenStorage

    override fun onCreate() {
        super.onCreate()

        // Schedule periodic calendar sync if user is signed in
        if (tokenStorage.isSignedIn && !tokenStorage.selectedCalendarId.isNullOrBlank()) {
            CalendarSyncWorker.schedulePeriodicSync(this)
            CalendarSyncWorker.triggerImmediateSync(this)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
