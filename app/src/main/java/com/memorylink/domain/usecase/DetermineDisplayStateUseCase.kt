package com.memorylink.domain.usecase

import com.memorylink.domain.model.AppSettings
import com.memorylink.domain.model.CalendarEvent
import com.memorylink.domain.model.DisplayState
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/**
 * Use case for determining the current display state based on time and events.
 *
 * See .clinerules/40-state-machine.md for the state diagram and transition rules.
 *
 * States:
 * - SLEEP: current_time >= sleep_time OR current_time < wake_time
 * - AWAKE_WITH_EVENT: Within wake period AND next event exists
 * - AWAKE_NO_EVENT: Within wake period AND no events
 */
class DetermineDisplayStateUseCase
@Inject
constructor(private val getNextEventUseCase: GetNextEventUseCase) {

    /**
     * Determine the current display state.
     *
     * Note: Time is NOT embedded in the returned DisplayState. The UI reads live system time
     * directly for accurate clock display. This use case only determines the logical state
     * (awake/sleep/event) based on the current time.
     *
     * @param now Current date/time (used for state evaluation, not embedded in result)
     * @param events List of calendar events for today
     * @param settings Current app settings (sleep/wake times, format)
     * @return The display state to render
     */
    operator fun invoke(
            now: LocalDateTime,
            events: List<CalendarEvent>,
            settings: AppSettings
    ): DisplayState {
        val currentTime = now.toLocalTime()

        // Check if we're in sleep period
        if (isInSleepPeriod(currentTime, settings.sleepTime, settings.wakeTime)) {
            return DisplayState.Sleep(
                    use24HourFormat = settings.use24HourFormat,
                    showYearInDate = settings.showYearInDate
            )
        }

        // We're in wake period - check for events
        val nextEvent = getNextEventUseCase(now, events)

        return if (nextEvent != null) {
            DisplayState.AwakeWithEvent(
                    nextEventTitle = nextEvent.title,
                    nextEventTime = getDisplayTime(nextEvent),
                    use24HourFormat = settings.use24HourFormat,
                    showYearInDate = settings.showYearInDate
            )
        } else {
            DisplayState.AwakeNoEvent(
                    use24HourFormat = settings.use24HourFormat,
                    showYearInDate = settings.showYearInDate
            )
        }
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

    /**
     * Get the display time for an event.
     *
     * For all-day events, returns null (UI displays "TODAY IS" prefix). For timed events, returns
     * the actual start time (UI displays "AT [time]" prefix).
     */
    private fun getDisplayTime(event: CalendarEvent): LocalTime? {
        return if (event.isAllDay) {
            null
        } else {
            event.startTime.toLocalTime()
        }
    }
}
