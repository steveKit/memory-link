package com.memorylink.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides current time information and a tick flow for state re-evaluation.
 *
 * Interface allows for easy mocking in unit tests.
 */
interface TimeProvider {
    /**
     * Get the current date/time.
     */
    fun now(): LocalDateTime

    /**
     * Get current time only.
     */
    fun currentTime(): LocalTime

    /**
     * Get current date only.
     */
    fun currentDate(): LocalDate

    /**
     * Flow that emits the current time every minute.
     *
     * Per .clinerules/40-state-machine.md:
     * - Every 1 minute: Re-evaluate display state (for event time passing)
     */
    fun minuteTicks(): Flow<LocalDateTime>
}

/**
 * Production implementation using system clock.
 */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {

    private val zoneId: ZoneId = ZoneId.systemDefault()

    override fun now(): LocalDateTime = LocalDateTime.now(zoneId)

    override fun currentTime(): LocalTime = LocalTime.now(zoneId)

    override fun currentDate(): LocalDate = LocalDate.now(zoneId)

    override fun minuteTicks(): Flow<LocalDateTime> = flow {
        while (currentCoroutineContext().isActive) {
            emit(now())
            // Calculate delay until next minute boundary for precise timing
            val nowTime = now()
            val secondsUntilNextMinute = 60 - nowTime.second
            val nanosUntilNextMinute = 1_000_000_000L - nowTime.nano
            val delayMillis = (secondsUntilNextMinute * 1000L) + (nanosUntilNextMinute / 1_000_000L)
            delay(delayMillis.coerceAtLeast(1000L)) // At least 1 second to avoid tight loops
        }
    }
}
