package com.memorylink.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides current time information for state evaluation.
 *
 * Interface allows for easy mocking in unit tests.
 *
 * Note: Minute ticks are now handled by [StateTransitionScheduler] using AlarmManager for more
 * precise and reliable timing.
 */
interface TimeProvider {
    /** Get the current date/time. */
    fun now(): LocalDateTime

    /** Get current time only. */
    fun currentTime(): LocalTime

    /** Get current date only. */
    fun currentDate(): LocalDate

    /**
     * Check if the current time is within the sleep period.
     *
     * Sleep period wraps around midnight:
     * - If sleep > wake: sleep period is [sleep, midnight) and [midnight, wake)
     * - If sleep <= wake: (unusual but handle it) sleep period is [sleep, wake)
     *
     * @param currentTime The time to check
     * @param sleepTime When sleep period begins
     * @param wakeTime When sleep period ends
     * @return true if currentTime is within the sleep period
     */
    fun isInSleepPeriod(currentTime: LocalTime, sleepTime: LocalTime, wakeTime: LocalTime): Boolean
}

/** Production implementation using system clock. */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {

    private val zoneId: ZoneId = ZoneId.systemDefault()

    override fun now(): LocalDateTime = LocalDateTime.now(zoneId)

    override fun currentTime(): LocalTime = LocalTime.now(zoneId)

    override fun currentDate(): LocalDate = LocalDate.now(zoneId)

    override fun isInSleepPeriod(
            currentTime: LocalTime,
            sleepTime: LocalTime,
            wakeTime: LocalTime
    ): Boolean {
        return if (sleepTime.isAfter(wakeTime)) {
            // Normal case: sleep at night, wake in morning
            // Sleep period: sleepTime to midnight, then midnight to wakeTime
            currentTime >= sleepTime || currentTime < wakeTime
        } else {
            // Edge case: wake time is after sleep time (e.g., both in same half of day)
            currentTime >= sleepTime && currentTime < wakeTime
        }
    }
}
