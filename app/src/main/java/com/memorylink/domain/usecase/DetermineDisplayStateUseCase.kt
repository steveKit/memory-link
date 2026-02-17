package com.memorylink.domain.usecase

import com.memorylink.domain.model.AppSettings
import com.memorylink.domain.model.CalendarEvent
import com.memorylink.domain.model.DisplayState
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/**
 * Determines display state based on time and events. See: 40-state-machine.md
 *
 * States: SLEEP (outside wake period), AWAKE_WITH_EVENT, AWAKE_NO_EVENT. UI reads live system time
 * directly for clock display.
 */
class DetermineDisplayStateUseCase
@Inject
constructor(private val getNextEventUseCase: GetNextEventUseCase) {

    /** Determine display state. Time is NOT embedded in result (UI uses live clock). */
    operator fun invoke(
            now: LocalDateTime,
            events: List<CalendarEvent>,
            settings: AppSettings
    ): DisplayState {
        val currentTime = now.toLocalTime()
        val today = now.toLocalDate()

        // Check if we're in sleep period
        if (isInSleepPeriod(currentTime, settings.sleepTime, settings.wakeTime)) {
            return DisplayState.Sleep(
                    use24HourFormat = settings.use24HourFormat,
                    showYearInDate = settings.showYearInDate
            )
        }

        // We're in wake period - find next events
        val nextEvents = getNextEventUseCase(now, events)
        val allDayEvent = nextEvents.allDayEvent
        val timedEvent = nextEvents.timedEvent

        // If no events at all, return AwakeNoEvent
        if (allDayEvent == null && timedEvent == null) {
            return DisplayState.AwakeNoEvent(
                    use24HourFormat = settings.use24HourFormat,
                    showYearInDate = settings.showYearInDate
            )
        }

        // Build AwakeWithEvent state
        return DisplayState.AwakeWithEvent(
                // All-day event fields
                allDayEventTitle = allDayEvent?.title,
                allDayEventDate =
                        if (allDayEvent != null && allDayEvent.startTime.toLocalDate() != today) {
                            allDayEvent.startTime.toLocalDate()
                        } else {
                            null // null means "today"
                        },
                // Timed event fields
                timedEventTitle = timedEvent?.title,
                timedEventTime = timedEvent?.startTime?.toLocalTime(),
                timedEventDate =
                        if (timedEvent != null && timedEvent.startTime.toLocalDate() != today) {
                            timedEvent.startTime.toLocalDate()
                        } else {
                            null // null means "today"
                        },
                // Settings
                use24HourFormat = settings.use24HourFormat,
                showYearInDate = settings.showYearInDate
        )
    }

    /**
     * Check if the current time is within the sleep period.
     *
     * Sleep period wraps around midnight:
     * - If sleep > wake: sleep period is [sleep, midnight) and [midnight, wake)
     * - If sleep <= wake: (unusual but handle it) sleep period is [sleep, wake)
     */
    private fun isInSleepPeriod(
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
