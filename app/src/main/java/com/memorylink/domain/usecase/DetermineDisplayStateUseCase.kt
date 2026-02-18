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
            return buildSleepState(now, today, events, settings)
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
        // For multi-day all-day events, we need to pass both start and end dates
        // to enable "until" formatting for ongoing events
        val allDayStartDate = allDayEvent?.startTime?.toLocalDate()
        val allDayEndDate = allDayEvent?.endTime?.toLocalDate()
        val isMultiDayEvent =
                allDayEvent != null &&
                        allDayStartDate != null &&
                        allDayEndDate != null &&
                        allDayEndDate.minusDays(1) > allDayStartDate // endDate is exclusive

        return DisplayState.AwakeWithEvent(
                // All-day event fields
                allDayEventTitle = allDayEvent?.title,
                // For multi-day events: null if ongoing (started today or earlier), date if future
                // For single-day events: null if today, date if future
                allDayEventDate =
                        if (allDayEvent != null &&
                                        allDayStartDate != null &&
                                        allDayStartDate.isAfter(today)
                        ) {
                            // Future event - show start date
                            allDayStartDate
                        } else {
                            // Today or past (ongoing multi-day) - null signals "active now"
                            null
                        },
                // End date for multi-day events (exclusive, so subtract 1 for display)
                allDayEventEndDate =
                        if (isMultiDayEvent) {
                            allDayEndDate?.minusDays(
                                    1
                            ) // Convert exclusive to inclusive for display
                        } else {
                            null
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
     * Build Sleep state, optionally with event data if showEventsDuringSleep is enabled.
     *
     * When the setting is disabled, returns a Sleep state with no event data.
     * When enabled, populates event data similar to AwakeWithEvent for dimmed display.
     */
    private fun buildSleepState(
            now: java.time.LocalDateTime,
            today: java.time.LocalDate,
            events: List<CalendarEvent>,
            settings: AppSettings
    ): DisplayState.Sleep {
        // If showEventsDuringSleep is disabled, return basic Sleep state
        if (!settings.showEventsDuringSleep) {
            return DisplayState.Sleep(
                    use24HourFormat = settings.use24HourFormat,
                    showYearInDate = settings.showYearInDate
            )
        }

        // Find next events for display during sleep
        val nextEvents = getNextEventUseCase(now, events)
        val allDayEvent = nextEvents.allDayEvent
        val timedEvent = nextEvents.timedEvent

        // Calculate all-day event fields (same logic as AwakeWithEvent)
        val allDayStartDate = allDayEvent?.startTime?.toLocalDate()
        val allDayEndDate = allDayEvent?.endTime?.toLocalDate()
        val isMultiDayEvent =
                allDayEvent != null &&
                        allDayStartDate != null &&
                        allDayEndDate != null &&
                        allDayEndDate.minusDays(1) > allDayStartDate

        return DisplayState.Sleep(
                // All-day event fields
                allDayEventTitle = allDayEvent?.title,
                allDayEventDate =
                        if (allDayEvent != null &&
                                        allDayStartDate != null &&
                                        allDayStartDate.isAfter(today)
                        ) {
                            allDayStartDate
                        } else {
                            null
                        },
                allDayEventEndDate =
                        if (isMultiDayEvent) {
                            allDayEndDate?.minusDays(1)
                        } else {
                            null
                        },
                // Timed event fields
                timedEventTitle = timedEvent?.title,
                timedEventTime = timedEvent?.startTime?.toLocalTime(),
                timedEventDate =
                        if (timedEvent != null && timedEvent.startTime.toLocalDate() != today) {
                            timedEvent.startTime.toLocalDate()
                        } else {
                            null
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
