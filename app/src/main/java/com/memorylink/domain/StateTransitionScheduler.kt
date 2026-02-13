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
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules precise state transitions using AlarmManager.
 *
 * This replaces the unreliable Flow-based minuteTicks() approach with system-level alarms that fire
 * precisely at:
 * - Wake time: Transition to awake state + trigger calendar sync
 * - Sleep time: Transition to sleep state + cancel calendar sync
 * - Minute boundaries: Update clock display (only during awake period)
 *
 * Per .clinerules/40-state-machine.md:
 * - Every 1 minute: Re-evaluate display state (for event time passing)
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
        const val ALARM_REQUEST_MINUTE_TICK = 1003

        // Intent actions
        const val ACTION_WAKE = "com.memorylink.ACTION_WAKE"
        const val ACTION_SLEEP = "com.memorylink.ACTION_SLEEP"
        const val ACTION_MINUTE_TICK = "com.memorylink.ACTION_MINUTE_TICK"
    }

    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private var isMinuteTickActive = false

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

        // Start minute ticks if we're currently in awake period
        if (isAwakePeriod(settings)) {
            startMinuteTicks()
        } else {
            stopMinuteTicks()
        }
    }

    /** Reschedule all alarms when settings change. */
    fun onSettingsChanged(newSettings: AppSettings) {
        Log.d(TAG, "Settings changed, rescheduling alarms")
        scheduleWakeAlarm(newSettings)
        scheduleSleepAlarm(newSettings)

        // Update minute tick state based on new settings
        if (isAwakePeriod(newSettings)) {
            startMinuteTicks()
        } else {
            stopMinuteTicks()
        }
    }

    /** Called when wake alarm fires. Starts minute ticks for clock updates during awake period. */
    fun onWakeAlarmFired() {
        Log.d(TAG, "Wake alarm fired")
        startMinuteTicks()

        // Reschedule wake alarm for tomorrow
        val settings = settingsRepository.currentSettings
        scheduleWakeAlarm(settings)
    }

    /** Called when sleep alarm fires. Stops minute ticks to save battery during sleep period. */
    fun onSleepAlarmFired() {
        Log.d(TAG, "Sleep alarm fired")
        stopMinuteTicks()

        // Reschedule sleep alarm for tomorrow
        val settings = settingsRepository.currentSettings
        scheduleSleepAlarm(settings)
    }

    /**
     * Called when minute tick alarm fires. Reschedules the next minute tick (only if still in awake
     * period).
     */
    fun onMinuteTickFired() {
        val settings = settingsRepository.currentSettings
        if (isAwakePeriod(settings)) {
            scheduleNextMinuteTick()
        } else {
            // We've somehow entered sleep period, stop ticking
            Log.d(TAG, "Minute tick fired but in sleep period, stopping ticks")
            isMinuteTickActive = false
        }
    }

    /** Check if current time is within the awake period. */
    fun isAwakePeriod(settings: AppSettings = settingsRepository.currentSettings): Boolean {
        val currentTime = timeProvider.currentTime()
        return !isInSleepPeriod(currentTime, settings.sleepTime, settings.wakeTime)
    }

    /** Stop all scheduled alarms. Called on app shutdown. */
    fun cancelAll() {
        Log.d(TAG, "Cancelling all alarms")
        cancelAlarm(ALARM_REQUEST_WAKE, ACTION_WAKE)
        cancelAlarm(ALARM_REQUEST_SLEEP, ACTION_SLEEP)
        cancelAlarm(ALARM_REQUEST_MINUTE_TICK, ACTION_MINUTE_TICK)
        isMinuteTickActive = false
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

    private fun startMinuteTicks() {
        if (isMinuteTickActive) {
            Log.d(TAG, "Minute ticks already active")
            return
        }
        Log.d(TAG, "Starting minute ticks")
        isMinuteTickActive = true
        scheduleNextMinuteTick()
    }

    private fun stopMinuteTicks() {
        if (!isMinuteTickActive) {
            return
        }
        Log.d(TAG, "Stopping minute ticks")
        cancelAlarm(ALARM_REQUEST_MINUTE_TICK, ACTION_MINUTE_TICK)
        isMinuteTickActive = false
    }

    private fun scheduleNextMinuteTick() {
        val now = timeProvider.now()

        // Calculate milliseconds until next minute boundary
        val nextMinute = now.plusMinutes(1).withSecond(0).withNano(0)
        val triggerAtMillis = nextMinute.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        scheduleExactAlarm(ALARM_REQUEST_MINUTE_TICK, ACTION_MINUTE_TICK, triggerAtMillis)
        Log.d(TAG, "Scheduled minute tick for $nextMinute")
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

    /**
     * Check if the current time is within the sleep period. Duplicated from
     * DetermineDisplayStateUseCase for independence.
     */
    private fun isInSleepPeriod(
            currentTime: LocalTime,
            sleepTime: LocalTime,
            wakeTime: LocalTime
    ): Boolean {
        return if (sleepTime.isAfter(wakeTime)) {
            // Normal case: sleep at night, wake in morning
            currentTime >= sleepTime || currentTime < wakeTime
        } else {
            // Edge case: wake time is after sleep time
            currentTime >= sleepTime && currentTime < wakeTime
        }
    }
}
