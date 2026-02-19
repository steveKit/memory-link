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
 * Architecture:
 * - Primary sync: KioskForegroundService runs every 15 minutes with high priority
 * - Backup sync: This WorkManager job runs once daily as a safety net
 * - Immediate sync: Available via admin panel "Sync Now" button
 *
 * The daily backup ensures data freshness even if the foreground service is killed
 * by aggressive battery optimizations on some devices.
 *
 * StateCoordinator observes Room directly, so events propagate automatically when
 * inserted/deleted.
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
            // Sync main calendar (daily backup - primary sync is via KioskForegroundService)
            val mainSyncResult = repository.syncEvents()

            when (mainSyncResult) {
                is CalendarRepository.SyncResult.Success -> {
                    Log.d(
                            TAG,
                            "Main sync completed: ${mainSyncResult.eventCount} events synced, ${mainSyncResult.deletedCount} deleted"
                    )
                }
                is CalendarRepository.SyncResult.NotAuthenticated -> {
                    Log.w(TAG, "Sync skipped: not authenticated")
                    return Result.success()
                }
                is CalendarRepository.SyncResult.NoCalendarSelected -> {
                    Log.w(TAG, "Sync skipped: no calendar selected")
                    return Result.success()
                }
                is CalendarRepository.SyncResult.Error -> {
                    Log.e(TAG, "Sync failed: ${mainSyncResult.message}")
                    return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                }
            }

            // Sync holiday calendar (weekly) - only if configured and due
            if (repository.hasHolidayCalendar && repository.needsHolidaySync) {
                val holidaySyncResult = repository.syncHolidayEvents()

                when (holidaySyncResult) {
                    is CalendarRepository.SyncResult.Success -> {
                        Log.d(
                                TAG,
                                "Holiday sync completed: ${holidaySyncResult.eventCount} events synced"
                        )
                    }
                    is CalendarRepository.SyncResult.NoCalendarSelected -> {
                        // Holiday calendar not configured - expected, not an error
                    }
                    is CalendarRepository.SyncResult.NotAuthenticated -> {
                        Log.w(TAG, "Holiday sync skipped: not authenticated")
                    }
                    is CalendarRepository.SyncResult.Error -> {
                        // Log but don't fail - main sync succeeded
                        Log.w(TAG, "Holiday sync failed: ${holidaySyncResult.message}")
                    }
                }
            }

            Result.success()
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

        /**
         * Schedule daily backup sync.
         *
         * This runs once per day as a safety net in case the foreground service is killed.
         * Primary sync happens every 15 minutes via KioskForegroundService.
         */
        fun scheduleDailyBackupSync(context: Context) {
            val constraints =
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

            val dailyWork =
                    PeriodicWorkRequestBuilder<CalendarSyncWorker>(24, TimeUnit.HOURS)
                            .setConstraints(constraints)
                            .setInitialDelay(1, TimeUnit.HOURS)
                            .build()

            WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                            PERIODIC_WORK_NAME,
                            ExistingPeriodicWorkPolicy.KEEP,
                            dailyWork
                    )

            Log.d(TAG, "Daily backup sync scheduled")
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
