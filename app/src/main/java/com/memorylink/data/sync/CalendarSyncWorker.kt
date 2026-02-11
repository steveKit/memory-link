package com.memorylink.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.memorylink.data.repository.CalendarRepository
import com.memorylink.data.repository.SettingsRepository
import com.memorylink.domain.StateCoordinator
import com.memorylink.domain.usecase.ParseConfigEventUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Background worker for syncing calendar events.
 *
 * Per .clinerules/10-project-meta.md:
 * - Sync Interval: Poll every 5 minutes for new events
 * - However, WorkManager minimum is 15 minutes for periodic work
 * - Config events are parsed immediately when cached
 *
 * Per .clinerules/20-android.md:
 * - Background sync continues during sleep mode (battery permitting)
 */
@HiltWorker
class CalendarSyncWorker
@AssistedInject
constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val repository: CalendarRepository,
        private val stateCoordinator: StateCoordinator,
        private val parseConfigEventUseCase: ParseConfigEventUseCase,
        private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting calendar sync")

        return try {
            // Perform sync
            val syncResult = repository.syncEvents(forceFullSync = runAttemptCount == 0)

            when (syncResult) {
                is CalendarRepository.SyncResult.Success -> {
                    Log.d(TAG, "Sync completed: ${syncResult.eventCount} events")

                    // Update StateCoordinator with new events
                    updateStateCoordinator()

                    Result.success()
                }
                is CalendarRepository.SyncResult.NotAuthenticated -> {
                    Log.w(TAG, "Sync skipped: not authenticated")
                    // Don't retry - user needs to sign in
                    Result.success()
                }
                is CalendarRepository.SyncResult.NoCalendarSelected -> {
                    Log.w(TAG, "Sync skipped: no calendar selected")
                    // Don't retry - user needs to select calendar
                    Result.success()
                }
                is CalendarRepository.SyncResult.Error -> {
                    Log.e(TAG, "Sync failed: ${syncResult.message}")
                    // Retry with backoff
                    if (runAttemptCount < MAX_RETRIES) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker exception", e)
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    /**
     * Update StateCoordinator with the latest events and process config events.
     *
     * Per .clinerules/10-project-meta.md:
     * - Config events are parsed immediately when cached
     * - Config events are never displayed to the memory user
     */
    private suspend fun updateStateCoordinator() {
        try {
            // 1. Process config events first (updates settings)
            val configEvents = repository.observeConfigEvents().first()
            if (configEvents.isNotEmpty()) {
                val appliedCount = parseConfigEventUseCase(configEvents)
                Log.d(TAG, "Processed $appliedCount config events")
            }

            // 2. Get updated settings after config processing
            val settings = settingsRepository.refreshSettings()
            stateCoordinator.updateSettings(settings)
            Log.d(TAG, "Settings updated: $settings")

            // 3. Update display events (non-config events only)
            val events = repository.observeTodaysEvents().first()
            stateCoordinator.updateEvents(events)
            Log.d(TAG, "StateCoordinator updated with ${events.size} display events")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update StateCoordinator", e)
        }
    }

    companion object {
        private const val TAG = "CalendarSyncWorker"
        private const val MAX_RETRIES = 3

        const val PERIODIC_WORK_NAME = "calendar_sync_periodic"
        const val IMMEDIATE_WORK_NAME = "calendar_sync_immediate"

        /**
         * Schedule periodic sync work.
         *
         * Note: WorkManager minimum interval is 15 minutes. Per .clinerules, we'd prefer 5 minutes,
         * but this is a platform limitation.
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints =
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

            val periodicWork =
                    PeriodicWorkRequestBuilder<CalendarSyncWorker>(
                                    15,
                                    TimeUnit.MINUTES // Minimum allowed by WorkManager
                            )
                            .setConstraints(constraints)
                            .setInitialDelay(1, TimeUnit.MINUTES) // Small delay on first run
                            .build()

            WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                            PERIODIC_WORK_NAME,
                            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
                            periodicWork
                    )

            Log.d(TAG, "Periodic sync scheduled (every 15 minutes)")
        }

        /** Trigger an immediate sync. Used on app start or after settings change. */
        fun triggerImmediateSync(context: Context) {
            val constraints =
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

            val immediateWork =
                    OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                            .setConstraints(constraints)
                            .build()

            WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                            IMMEDIATE_WORK_NAME,
                            ExistingWorkPolicy.REPLACE, // Replace any pending immediate sync
                            immediateWork
                    )

            Log.d(TAG, "Immediate sync triggered")
        }

        /** Cancel all sync work. Used on sign-out. */
        fun cancelAllSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK_NAME)
            Log.d(TAG, "All sync work cancelled")
        }
    }
}
