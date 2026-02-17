package com.memorylink.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.memorylink.MainActivity
import com.memorylink.R
import com.memorylink.data.repository.CalendarRepository
import com.memorylink.domain.StateCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that handles kiosk mode operations.
 *
 * Responsibilities:
 * - Calendar sync every 5 minutes (as per .clinerules/10-project-meta.md)
 * - State refresh every minute (for event time passing checks)
 *
 * This is more reliable than WorkManager for an always-on kiosk display:
 * - WorkManager has a minimum 15-minute interval
 * - Foreground service allows exact 5-minute sync intervals
 * - Service runs with high priority, resisting Doze mode delays
 *
 * Note: Clock display is handled by KioskScreen's rememberLiveTime() composable,
 * which updates every second using system time directly.
 */
@AndroidEntryPoint
class KioskForegroundService : Service() {

    companion object {
        private const val TAG = "KioskForegroundService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "kiosk_service_channel"

        /** Sync interval: 5 minutes as per clinerules */
        private const val SYNC_INTERVAL_MS = 5 * 60 * 1000L

        /** State refresh interval: 1 minute for event time checking */
        private const val STATE_REFRESH_INTERVAL_MS = 60 * 1000L

        /** Start the foreground service */
        fun start(context: Context) {
            val intent = Intent(context, KioskForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Service start requested")
        }

        /** Stop the foreground service */
        fun stop(context: Context) {
            val intent = Intent(context, KioskForegroundService::class.java)
            context.stopService(intent)
            Log.d(TAG, "Service stop requested")
        }
    }

    @Inject
    lateinit var calendarRepository: CalendarRepository

    @Inject
    lateinit var stateCoordinator: StateCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var syncJob: Job? = null
    private var stateRefreshJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // Start as foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Start the sync and refresh loops
        startSyncLoop()
        startStateRefreshLoop()

        // Trigger immediate sync on service start
        serviceScope.launch {
            performSync()
        }

        // Service should be restarted if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        syncJob?.cancel()
        stateRefreshJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Start the calendar sync loop.
     * Syncs every 5 minutes as per clinerules spec.
     */
    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                performSync()
            }
        }
        Log.d(TAG, "Sync loop started (every ${SYNC_INTERVAL_MS / 1000 / 60} minutes)")
    }

    /**
     * Start the state refresh loop.
     * Refreshes state every minute to check for event time passing.
     */
    private fun startStateRefreshLoop() {
        stateRefreshJob?.cancel()
        stateRefreshJob = serviceScope.launch {
            while (isActive) {
                delay(STATE_REFRESH_INTERVAL_MS)
                Log.d(TAG, "Minute tick: refreshing state")
                stateCoordinator.refreshState()
            }
        }
        Log.d(TAG, "State refresh loop started (every minute)")
    }

    /**
     * Perform a calendar sync.
     * Uses incremental sync with syncToken for efficiency.
     */
    private suspend fun performSync() {
        Log.d(TAG, "Performing calendar sync")
        try {
            val result = calendarRepository.syncEvents()
            when (result) {
                is CalendarRepository.SyncResult.Success -> {
                    Log.d(TAG, "Sync completed: ${result.eventCount} events, ${result.deletedCount} deleted")
                }
                is CalendarRepository.SyncResult.NotAuthenticated -> {
                    Log.w(TAG, "Sync skipped: not authenticated")
                }
                is CalendarRepository.SyncResult.NoCalendarSelected -> {
                    Log.w(TAG, "Sync skipped: no calendar selected")
                }
                is CalendarRepository.SyncResult.Error -> {
                    Log.e(TAG, "Sync failed: ${result.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync exception", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kiosk Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MemoryLink kiosk mode is active"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MemoryLink Active")
            .setContentText("Kiosk mode is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
