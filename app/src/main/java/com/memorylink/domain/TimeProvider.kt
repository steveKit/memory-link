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
}

/** Production implementation using system clock. */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {

    private val zoneId: ZoneId = ZoneId.systemDefault()

    override fun now(): LocalDateTime = LocalDateTime.now(zoneId)

    override fun currentTime(): LocalTime = LocalTime.now(zoneId)

    override fun currentDate(): LocalDate = LocalDate.now(zoneId)
}
