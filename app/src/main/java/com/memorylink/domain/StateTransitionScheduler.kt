package com.memorylink.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.memorylink.data.repository.SettingsRepository
import com.memorylink.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules wake/sleep state transitions using AlarmManager.
 *
 * Schedules alarms for:
 * - Wake time: Transition to awake state, start KioskForegroundService
 * - Sleep time: Transition to sleep state, stop KioskForegroundService
 *
 * Note: Minute ticks and calendar sync are handled by KioskForegroundService, which provides more
 * reliable 15-minute sync intervals than WorkManager and 1-minute state refresh for event time
 * checking.
 *
 * Per .clinerules/40-state-machine.md:
 * - At wake/sleep boundary: Immediate state transition
 */
@Singleton
class StateTransitionScheduler
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val timeProvider: TimeProvider
) {

    companion object {
        private const val TAG = "StateTransitionScheduler"

        // Alarm request codes
        const val ALARM_REQUEST_WAKE = 1001
        const val ALARM_REQUEST_SLEEP = 1002

        // Intent actions
        const val ACTION_WAKE = "com.memorylink.ACTION_WAKE"
        const val ACTION_SLEEP = "com.memorylink.ACTION_SLEEP"
    }

    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    /**
     * Initialize the scheduler with current settings. Call this on app startup and when settings
     * change.
     */
    fun initialize() {
        val settings = settingsRepository.currentSettings
        Log.d(
                TAG,
                "Initializing scheduler with settings: wake=${settings.wakeTime}, sleep=${settings.sleepTime}"
        )

        scheduleWakeAlarm(settings)
        scheduleSleepAlarm(settings)
    }

    /** Reschedule all alarms when settings change. */
    fun onSettingsChanged(newSettings: AppSettings) {
        Log.d(TAG, "Settings changed, rescheduling alarms")
        scheduleWakeAlarm(newSettings)
        scheduleSleepAlarm(newSettings)
    }

    /** Called when wake alarm fires. Reschedules wake alarm for tomorrow. */
    fun onWakeAlarmFired() {
        Log.d(TAG, "Wake alarm fired")
        // Reschedule wake alarm for tomorrow
        val settings = settingsRepository.currentSettings
        scheduleWakeAlarm(settings)
    }

    /** Called when sleep alarm fires. Reschedules sleep alarm for tomorrow. */
    fun onSleepAlarmFired() {
        Log.d(TAG, "Sleep alarm fired")
        // Reschedule sleep alarm for tomorrow
        val settings = settingsRepository.currentSettings
        scheduleSleepAlarm(settings)
    }

    /** Check if current time is within the awake period. */
    fun isAwakePeriod(settings: AppSettings = settingsRepository.currentSettings): Boolean {
        val currentTime = timeProvider.currentTime()
        return !timeProvider.isInSleepPeriod(currentTime, settings.sleepTime, settings.wakeTime)
    }

    /** Stop all scheduled alarms. Called on app shutdown. */
    fun cancelAll() {
        Log.d(TAG, "Cancelling all alarms")
        cancelAlarm(ALARM_REQUEST_WAKE, ACTION_WAKE)
        cancelAlarm(ALARM_REQUEST_SLEEP, ACTION_SLEEP)
    }

    private fun scheduleWakeAlarm(settings: AppSettings) {
        val wakeTime = settings.wakeTime
        val now = timeProvider.now()

        // Calculate next wake time
        var wakeDateTime = LocalDateTime.of(now.toLocalDate(), wakeTime)
        if (wakeDateTime.isBefore(now) || wakeDateTime.isEqual(now)) {
            // Wake time already passed today, schedule for tomorrow
            wakeDateTime = wakeDateTime.plusDays(1)
        }

        val triggerAtMillis = wakeDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        scheduleExactAlarm(ALARM_REQUEST_WAKE, ACTION_WAKE, triggerAtMillis)
        Log.d(TAG, "Scheduled wake alarm for $wakeDateTime")
    }

    private fun scheduleSleepAlarm(settings: AppSettings) {
        val sleepTime = settings.sleepTime
        val now = timeProvider.now()

        // Calculate next sleep time
        var sleepDateTime = LocalDateTime.of(now.toLocalDate(), sleepTime)
        if (sleepDateTime.isBefore(now) || sleepDateTime.isEqual(now)) {
            // Sleep time already passed today, schedule for tomorrow
            sleepDateTime = sleepDateTime.plusDays(1)
        }

        val triggerAtMillis =
                sleepDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        scheduleExactAlarm(ALARM_REQUEST_SLEEP, ACTION_SLEEP, triggerAtMillis)
        Log.d(TAG, "Scheduled sleep alarm for $sleepDateTime")
    }

    private fun scheduleExactAlarm(requestCode: Int, action: String, triggerAtMillis: Long) {
        val intent = Intent(action).apply { setPackage(context.packageName) }
        val pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ requires exact alarm permission check
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                    )
                } else {
                    // Fallback to inexact alarm if permission not granted
                    Log.w(TAG, "Exact alarm permission not granted, using inexact alarm")
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule exact alarm", e)
            // Fallback to inexact
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
            )
        }
    }

    private fun cancelAlarm(requestCode: Int, action: String) {
        val intent = Intent(action).apply { setPackage(context.packageName) }
        val pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
}
