package com.memorylink

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Main Application class for MemoryLink.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 * Implements WorkManager Configuration for Hilt-aware Workers.
 */
@HiltAndroidApp
class MemoryLinkApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
