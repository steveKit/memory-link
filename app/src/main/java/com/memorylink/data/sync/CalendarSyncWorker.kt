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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background worker for syncing calendar events.
 *
 * WorkManager minimum is 15 minutes (we'd prefer 5). StateCoordinator observes Room directly, so
 * events propagate automatically when inserted/deleted.
 */
@HiltWorker
class CalendarSyncWorker
@AssistedInject
constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val repository: CalendarRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting calendar sync")

        return try {
            val syncResult = repository.syncEvents()

            when (syncResult) {
                is CalendarRepository.SyncResult.Success -> {
                    Log.d(
                            TAG,
                            "Sync completed: ${syncResult.eventCount} events synced, ${syncResult.deletedCount} deleted"
                    )
                    Result.success()
                }
                is CalendarRepository.SyncResult.NotAuthenticated -> {
                    Log.w(TAG, "Sync skipped: not authenticated")
                    Result.success()
                }
                is CalendarRepository.SyncResult.NoCalendarSelected -> {
                    Log.w(TAG, "Sync skipped: no calendar selected")
                    Result.success()
                }
                is CalendarRepository.SyncResult.Error -> {
                    Log.e(TAG, "Sync failed: ${syncResult.message}")
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker exception", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "CalendarSyncWorker"
        private const val MAX_RETRIES = 3

        const val PERIODIC_WORK_NAME = "calendar_sync_periodic"
        const val IMMEDIATE_WORK_NAME = "calendar_sync_immediate"

        /** Schedule periodic sync (15-minute minimum per WorkManager). */
        fun schedulePeriodicSync(context: Context) {
            val constraints =
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

            val periodicWork =
                    PeriodicWorkRequestBuilder<CalendarSyncWorker>(15, TimeUnit.MINUTES)
                            .setConstraints(constraints)
                            .setInitialDelay(1, TimeUnit.MINUTES)
                            .build()

            WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                            PERIODIC_WORK_NAME,
                            ExistingPeriodicWorkPolicy.KEEP,
                            periodicWork
                    )

            Log.d(TAG, "Periodic sync scheduled (every 15 minutes)")
        }

        /** Trigger immediate sync (app start, settings change). */
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
                            ExistingWorkPolicy.REPLACE,
                            immediateWork
                    )

            Log.d(TAG, "Immediate sync triggered")
        }

        /** Cancel all sync work (used on sign-out). */
        fun cancelAllSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK_NAME)
            Log.d(TAG, "All sync work cancelled")
        }
    }
}
