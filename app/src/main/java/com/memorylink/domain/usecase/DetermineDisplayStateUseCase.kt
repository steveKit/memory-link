package com.memorylink.domain.usecase

import com.memorylink.domain.TimeProvider
import com.memorylink.domain.model.AllDayEventInfo
import com.memorylink.domain.model.AppSettings
import com.memorylink.domain.model.CalendarEvent
import com.memorylink.domain.model.DisplayState
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Determines display state based on time and events. See: 40-state-machine.md
 *
 * States: SLEEP (outside wake period), AWAKE_WITH_EVENT, AWAKE_NO_EVENT. UI reads live system time
 * directly for clock display.
 */
class DetermineDisplayStateUseCase
@Inject
constructor(
        private val getNextEventUseCase: GetNextEventUseCase,
        private val timeProvider: TimeProvider
) {

    /** Determine display state. Time is NOT embedded in result (UI uses live clock). */
    operator fun invoke(
            now: LocalDateTime,
            events: List<CalendarEvent>,
            settings: AppSettings
    ): DisplayState {
        val currentTime = now.toLocalTime()
        val today = now.toLocalDate()

        // Check if any timed events today haven't ended yet
        // This is used to delay sleep until after the last event
        val hasRemainingTimedEventsToday =
                events.any { event ->
                    !event.isAllDay &&
                            event.startTime.toLocalDate() == today &&
                            event.endTime.isAfter(now)
                }

        // Check if we're in sleep period
        // Sleep is delayed if there are remaining timed events today
        if (timeProvider.isInSleepPeriod(currentTime, settings.sleepTime, settings.wakeTime) &&
                        !hasRemainingTimedEventsToday
        ) {
            return buildSleepState(now, today, events, settings)
        }

        // We're in wake period - find next events
        val nextEvents = getNextEventUseCase(now, events)
        val allDayEvents = nextEvents.allDayEvents
        val timedEvent = nextEvents.timedEvent

        // If no events at all, return AwakeNoEvent
        if (allDayEvents.isEmpty() && timedEvent == null) {
            return DisplayState.AwakeNoEvent(
                    use24HourFormat = settings.use24HourFormat,
                    showYearInDate = settings.showYearInDate,
                    brightness = settings.brightness
            )
        }

        // Convert CalendarEvents to AllDayEventInfo for display
        val allDayEventInfos = allDayEvents.map { event -> convertToAllDayEventInfo(event, today) }

        return DisplayState.AwakeWithEvent(
                // All-day events (each shown on separate line)
                allDayEvents = allDayEventInfos,
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
                showYearInDate = settings.showYearInDate,
                brightness = settings.brightness
        )
    }

    /**
     * Convert a CalendarEvent to AllDayEventInfo for display.
     *
     * @param event The calendar event
     * @param today Today's date
     * @return AllDayEventInfo with appropriate start/end dates
     */
    private fun convertToAllDayEventInfo(event: CalendarEvent, today: LocalDate): AllDayEventInfo {
        val startDate = event.startTime.toLocalDate()
        val endDate = event.endTime.toLocalDate()
        val isMultiDay = endDate.minusDays(1) > startDate // endDate is exclusive

        return AllDayEventInfo(
                title = event.title,
                // null if today/ongoing, otherwise the start date
                startDate = if (startDate.isAfter(today)) startDate else null,
                // End date for multi-day (exclusive, so subtract 1 for display)
                endDate = if (isMultiDay) endDate.minusDays(1) else null
        )
    }

    /**
     * Build Sleep state, optionally with event data if showEventsDuringSleep is enabled.
     *
     * When the setting is disabled, returns a Sleep state with no event data. When enabled,
     * populates event data similar to AwakeWithEvent for dimmed display.
     */
    private fun buildSleepState(
            now: LocalDateTime,
            today: LocalDate,
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
        val allDayEvents = nextEvents.allDayEvents
        val timedEvent = nextEvents.timedEvent

        // Convert CalendarEvents to AllDayEventInfo for display
        val allDayEventInfos = allDayEvents.map { event -> convertToAllDayEventInfo(event, today) }

        return DisplayState.Sleep(
                // All-day events (each shown on separate line)
                allDayEvents = allDayEventInfos,
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
}
