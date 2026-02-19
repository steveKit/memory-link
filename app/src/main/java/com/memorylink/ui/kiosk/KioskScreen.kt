package com.memorylink.ui.kiosk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import com.memorylink.domain.model.AllDayEventInfo
import com.memorylink.domain.model.DisplayState
import com.memorylink.ui.theme.DarkBackground
import com.memorylink.ui.theme.DarkSurface
import com.memorylink.ui.theme.DisplayConstants
import com.memorylink.ui.theme.MemoryLinkTheme
import com.memorylink.ui.theme.SleepBackground
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * Composable that provides live system time, updating every second.
 *
 * This ensures the clock display is always accurate regardless of when state evaluations occur
 * (which may be delayed by Doze mode, etc.).
 *
 * @return Pair of (currentTime, currentDate) that updates every second
 */
@Composable
fun rememberLiveTime(): Pair<LocalTime, LocalDate> {
        val zoneId = ZoneId.systemDefault()
        var currentTime by remember { mutableStateOf(LocalTime.now(zoneId)) }
        var currentDate by remember { mutableStateOf(LocalDate.now(zoneId)) }

        LaunchedEffect(Unit) {
                while (true) {
                        currentTime = LocalTime.now(zoneId)
                        currentDate = LocalDate.now(zoneId)
                        // Update every second for smooth clock display
                        delay(1000L)
                }
        }

        return currentTime to currentDate
}

/**
 * Main kiosk screen that renders the appropriate UI based on DisplayState.
 *
 * States:
 * - AwakeNoEvent: Clock + date centered, full brightness
 * - AwakeWithEvent: Clock + date + optional all-day event (in clock area) + optional timed event
 * (in event card)
 * - Sleep: Clock + date centered with dimmed colors (reuses ClockDisplay)
 *
 * Background: #121212 (DarkBackground), #0A0A0A (SleepBackground) in sleep mode
 *
 * Layout philosophy:
 * - All content is centered as a unit (no fixed percentage splits)
 * - Text auto-sizes to fill available space with max limits
 * - Same ClockDisplay component used for all states (DRY)
 * - Time is read live from system clock (not from DisplayState)
 *
 * @param displayState The current display state to render
 * @param modifier Modifier for the root Box
 */
@Composable
fun KioskScreen(displayState: DisplayState, modifier: Modifier = Modifier) {
        // Always use live system time for accurate clock display
        val (currentTime, currentDate) = rememberLiveTime()

        Box(
                modifier = modifier.fillMaxSize().background(DarkBackground),
                contentAlignment = Alignment.Center
        ) {
                when (displayState) {
                        is DisplayState.AwakeNoEvent -> {
                                AwakeNoEventContent(
                                        currentTime = currentTime,
                                        currentDate = currentDate,
                                        use24HourFormat = displayState.use24HourFormat,
                                        showYearInDate = displayState.showYearInDate
                                )
                        }
                        is DisplayState.AwakeWithEvent -> {
                                AwakeWithEventContent(
                                        currentTime = currentTime,
                                        currentDate = currentDate,
                                        use24HourFormat = displayState.use24HourFormat,
                                        showYearInDate = displayState.showYearInDate,
                                        // All-day events (clock area)
                                        allDayEvents = displayState.allDayEvents,
                                        // Timed event (event card)
                                        timedEventTitle = displayState.timedEventTitle,
                                        timedEventTime = displayState.timedEventTime,
                                        timedEventDate = displayState.timedEventDate
                                )
                        }
                        is DisplayState.Sleep -> {
                                SleepContent(
                                        currentTime = currentTime,
                                        currentDate = currentDate,
                                        use24HourFormat = displayState.use24HourFormat,
                                        showYearInDate = displayState.showYearInDate,
                                        // All-day events (clock area)
                                        allDayEvents = displayState.allDayEvents,
                                        // Timed event (event card)
                                        timedEventTitle = displayState.timedEventTitle,
                                        timedEventTime = displayState.timedEventTime,
                                        timedEventDate = displayState.timedEventDate
                                )
                        }
                }
        }
}

/** Content displayed when awake with no events. Clock + date centered on screen. */
@Composable
private fun AwakeNoEventContent(
        currentTime: LocalTime,
        currentDate: LocalDate,
        use24HourFormat: Boolean,
        showYearInDate: Boolean,
        modifier: Modifier = Modifier
) {
        Box(
                modifier = modifier.fillMaxSize().padding(DisplayConstants.SCREEN_MARGIN),
                contentAlignment = Alignment.Center
        ) {
                ClockDisplay(
                        time = currentTime,
                        date = currentDate,
                        use24HourFormat = use24HourFormat,
                        showYearInDate = showYearInDate,
                        colorScheme = ClockColorScheme.Awake,
                        modifier = Modifier.fillMaxSize()
                )
        }
}

/**
 * Content displayed when awake with event(s).
 *
 * Layout:
 * - Clock area (top): Time + Date + all-day events (each on separate line, in AccentBlue)
 * - Event card (bottom): Timed event (only shown if timedEventTitle is not null)
 *
 * Uses layered layout with dynamic background:
 * - Content is centered as a single block (clock + event)
 * - Equal space above clock = space below event text
 * - Background layer follows the EventCard's actual position (edge-to-edge, top corners rounded)
 * - Clock text is bottom-justified, event text is top-justified
 * - Text content respects SCREEN_MARGIN, background extends to screen edges
 */
@Composable
private fun AwakeWithEventContent(
        currentTime: LocalTime,
        currentDate: LocalDate,
        use24HourFormat: Boolean,
        showYearInDate: Boolean,
        // All-day events (displayed in clock area, each on separate line)
        allDayEvents: List<AllDayEventInfo>,
        // Timed event fields
        timedEventTitle: String?,
        timedEventTime: LocalTime?,
        timedEventDate: LocalDate?,
        modifier: Modifier = Modifier
) {
        val hasTimedEvent = timedEventTitle != null && timedEventTime != null
        var eventCardYOffset by remember { mutableFloatStateOf(0f) }

        Box(modifier = modifier.fillMaxSize()) {
                // Background layer: edge-to-edge, positioned at EventCard's Y position, extends to
                // bottom (only if timed event exists)
                if (hasTimedEvent && eventCardYOffset > 0f) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .fillMaxSize()
                                                .offset { IntOffset(0, eventCardYOffset.toInt()) }
                                                .clip(
                                                        RoundedCornerShape(
                                                                topStart =
                                                                        DisplayConstants
                                                                                .EVENT_CARD_CORNER_RADIUS,
                                                                topEnd =
                                                                        DisplayConstants
                                                                                .EVENT_CARD_CORNER_RADIUS
                                                        )
                                                )
                                                .background(DarkSurface)
                        )
                }

                // Content layer: centered as a group with weighted spacers, respects SCREEN_MARGIN
                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .padding(horizontal = DisplayConstants.SCREEN_MARGIN),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        // Top spacer - creates equal margin above clock
                        Spacer(modifier = Modifier.weight(1f))

                        // Clock area - bottom justified within its natural size
                        // Includes all-day events (each on separate line)
                        Box(contentAlignment = Alignment.BottomCenter) {
                                ClockDisplay(
                                        time = currentTime,
                                        date = currentDate,
                                        use24HourFormat = use24HourFormat,
                                        showYearInDate = showYearInDate,
                                        allDayEvents = allDayEvents,
                                        colorScheme = ClockColorScheme.Awake,
                                        modifier = Modifier.fillMaxWidth()
                                )
                        }

                        // Event card - only show if timed event exists
                        if (hasTimedEvent) {
                                Spacer(
                                        modifier =
                                                Modifier.height(
                                                        DisplayConstants.CLOCK_TO_EVENT_SPACING
                                                )
                                )

                                // Event text - top justified, tracks its position for background
                                // layer
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth().onGloballyPositioned {
                                                        coords ->
                                                        eventCardYOffset =
                                                                coords.positionInParent().y
                                                },
                                        contentAlignment = Alignment.TopStart
                                ) {
                                        EventCard(
                                                title = timedEventTitle!!,
                                                startTime = timedEventTime!!,
                                                eventDate = timedEventDate,
                                                use24HourFormat = use24HourFormat,
                                                showBackground = false,
                                                modifier = Modifier.fillMaxWidth()
                                        )
                                }
                        }

                        // Bottom spacer - creates equal margin below event (matches top)
                        Spacer(modifier = Modifier.weight(1f))
                }
        }
}

/**
 * Content displayed during sleep mode.
 *
 * When showEventsDuringSleep is disabled: Same layout as AwakeNoEvent but with dimmed colors and
 * darker background.
 *
 * When showEventsDuringSleep is enabled: Shows clock + optional timed event card (NO all-day
 * events in sleep mode), all with dimmed colors.
 *
 * Uses 1-second cross-fade animation when entering/exiting.
 */
@Composable
private fun SleepContent(
        currentTime: LocalTime,
        currentDate: LocalDate,
        use24HourFormat: Boolean,
        showYearInDate: Boolean,
        // All-day events are NOT displayed during sleep mode (ignored)
        @Suppress("UNUSED_PARAMETER") allDayEvents: List<AllDayEventInfo> = emptyList(),
        // Timed event fields (optional - only present if showEventsDuringSleep is enabled)
        timedEventTitle: String? = null,
        timedEventTime: LocalTime? = null,
        timedEventDate: LocalDate? = null,
        modifier: Modifier = Modifier
) {
        val hasTimedEvent = timedEventTitle != null && timedEventTime != null

        AnimatedVisibility(
                visible = true,
                enter =
                        fadeIn(
                                animationSpec =
                                        tween(
                                                durationMillis =
                                                        DisplayConstants
                                                                .STATE_TRANSITION_DURATION_MS
                                        )
                        ),
                exit =
                        fadeOut(
                                animationSpec =
                                        tween(
                                                durationMillis =
                                                        DisplayConstants
                                                                .STATE_TRANSITION_DURATION_MS
                                        )
                        ),
                modifier = modifier.fillMaxSize()
        ) {
                if (hasTimedEvent) {
                        // Sleep mode with timed event - clock + event card, all dimmed
                        SleepWithEventLayout(
                                currentTime = currentTime,
                                currentDate = currentDate,
                                use24HourFormat = use24HourFormat,
                                showYearInDate = showYearInDate,
                                timedEventTitle = timedEventTitle,
                                timedEventTime = timedEventTime,
                                timedEventDate = timedEventDate
                        )
                } else {
                        // Sleep mode without events - simple centered clock
                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(SleepBackground)
                                                .padding(DisplayConstants.SLEEP_MARGIN),
                                contentAlignment = Alignment.Center
                        ) {
                                ClockDisplay(
                                        time = currentTime,
                                        date = currentDate,
                                        use24HourFormat = use24HourFormat,
                                        showYearInDate = showYearInDate,
                                        colorScheme = ClockColorScheme.Sleep,
                                        modifier = Modifier.fillMaxSize()
                                )
                        }
                }
        }
}

/**
 * Layout for sleep mode when timed event is shown. Shows clock + timed event card with dimmed
 * colors. All-day events are NOT displayed during sleep mode.
 */
@Composable
private fun SleepWithEventLayout(
        currentTime: LocalTime,
        currentDate: LocalDate,
        use24HourFormat: Boolean,
        showYearInDate: Boolean,
        timedEventTitle: String?,
        timedEventTime: LocalTime?,
        timedEventDate: LocalDate?
) {
        val hasTimedEvent = timedEventTitle != null && timedEventTime != null

        Box(modifier = Modifier.fillMaxSize().background(SleepBackground)) {
                // Content layer: centered as a group with weighted spacers
                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .padding(horizontal = DisplayConstants.SCREEN_MARGIN),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        // Top spacer
                        Spacer(modifier = Modifier.weight(1f))

                        // Clock area (no all-day events in sleep mode)
                        Box(contentAlignment = Alignment.BottomCenter) {
                                ClockDisplay(
                                        time = currentTime,
                                        date = currentDate,
                                        use24HourFormat = use24HourFormat,
                                        showYearInDate = showYearInDate,
                                        colorScheme = ClockColorScheme.Sleep,
                                        modifier = Modifier.fillMaxWidth()
                                )
                        }

                        // Event card - only show if timed event exists
                        if (hasTimedEvent) {
                                Spacer(
                                        modifier =
                                                Modifier.height(
                                                        DisplayConstants.CLOCK_TO_EVENT_SPACING
                                                )
                                )

                                Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.TopStart
                                ) {
                                        EventCard(
                                                title = timedEventTitle!!,
                                                startTime = timedEventTime!!,
                                                eventDate = timedEventDate,
                                                use24HourFormat = use24HourFormat,
                                                showBackground = false,
                                                colorScheme = EventCardColorScheme.Sleep,
                                                modifier = Modifier.fillMaxWidth()
                                        )
                                }
                        }

                        // Bottom spacer
                        Spacer(modifier = Modifier.weight(1f))
                }
        }
}

// region Previews

@Preview(
        name = "Kiosk Screen - Awake No Event (Landscape)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenAwakeNoEventPreview() {
        MemoryLinkTheme {
                KioskScreen(displayState = DisplayState.AwakeNoEvent(use24HourFormat = false, showYearInDate = false))
        }
}

@Preview(
        name = "Kiosk Screen - Awake No Event (Portrait)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 480,
        heightDp = 800
)
@Composable
private fun KioskScreenAwakeNoEventPortraitPreview() {
        MemoryLinkTheme {
                KioskScreen(displayState = DisplayState.AwakeNoEvent(use24HourFormat = false, showYearInDate = false))
        }
}

@Preview(
        name = "Kiosk Screen - Timed Event Today (Landscape)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenTimedEventTodayPreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        timedEventTitle = "Doctor Appointment",
                                        timedEventTime = LocalTime.of(10, 30),
                                        timedEventDate = null, // null = today
                                        use24HourFormat = false,
                                        showYearInDate = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - Timed Event Future (Landscape)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenTimedEventFuturePreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        timedEventTitle = "Doctor Appointment",
                                        timedEventTime = LocalTime.of(10, 30),
                                        timedEventDate = LocalDate.of(2026, 2, 19),
                                        use24HourFormat = false,
                                        showYearInDate = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - All-Day Event Today",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenAllDayTodayPreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        allDayEvents = listOf(
                                                AllDayEventInfo(title = "Mom's Birthday")
                                        ),
                                        use24HourFormat = false,
                                        showYearInDate = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - All-Day Event Tomorrow",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenAllDayTomorrowPreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        allDayEvents = listOf(
                                                AllDayEventInfo(
                                                        title = "Family Reunion",
                                                        startDate = LocalDate.now().plusDays(1)
                                                )
                                        ),
                                        use24HourFormat = false,
                                        showYearInDate = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - All-Day Event Future",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenAllDayFuturePreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        allDayEvents = listOf(
                                                AllDayEventInfo(
                                                        title = "Company Party",
                                                        startDate = LocalDate.of(2026, 2, 20)
                                                )
                                        ),
                                        use24HourFormat = false,
                                        showYearInDate = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - All-Day + Timed Event",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenAllDayPlusTimedPreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        allDayEvents = listOf(
                                                AllDayEventInfo(title = "Mom's Birthday")
                                        ),
                                        timedEventTitle = "Doctor Appointment",
                                        timedEventTime = LocalTime.of(14, 30),
                                        timedEventDate = null,
                                        use24HourFormat = false,
                                        showYearInDate = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - Multiple All-Day Events",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenMultipleAllDayPreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        allDayEvents = listOf(
                                                AllDayEventInfo(title = "Family Day"), // Holiday (today)
                                                AllDayEventInfo(title = "Mom's Birthday"), // Today
                                                AllDayEventInfo(
                                                        title = "Valentine's Day",
                                                        startDate = LocalDate.now().plusDays(1)
                                                ) // Tomorrow
                                        ),
                                        timedEventTitle = "Physical Therapy",
                                        timedEventTime = LocalTime.of(10, 0),
                                        timedEventDate = LocalDate.of(2026, 2, 19),
                                        use24HourFormat = false,
                                        showYearInDate = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - Sleep Mode (Landscape)",
        showBackground = true,
        backgroundColor = 0xFF0A0A0A,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenSleepPreview() {
        MemoryLinkTheme { KioskScreen(displayState = DisplayState.Sleep(use24HourFormat = false, showYearInDate = false)) }
}

@Preview(
        name = "Kiosk Screen - Sleep Mode (Portrait)",
        showBackground = true,
        backgroundColor = 0xFF0A0A0A,
        widthDp = 480,
        heightDp = 800
)
@Composable
private fun KioskScreenSleepPortraitPreview() {
        MemoryLinkTheme { KioskScreen(displayState = DisplayState.Sleep(use24HourFormat = false, showYearInDate = false)) }
}

@Preview(
        name = "Kiosk Screen - Sleep Mode With Event",
        showBackground = true,
        backgroundColor = 0xFF0A0A0A,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenSleepWithEventPreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.Sleep(
                                        timedEventTitle = "Doctor Appointment",
                                        timedEventTime = LocalTime.of(10, 30),
                                        timedEventDate = null,
                                        use24HourFormat = false,
                                        showYearInDate = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - Sleep Mode With Timed Event",
        showBackground = true,
        backgroundColor = 0xFF0A0A0A,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenSleepWithTimedPreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.Sleep(
                                        timedEventTitle = "Lunch Celebration",
                                        timedEventTime = LocalTime.of(12, 0),
                                        timedEventDate = null,
                                        use24HourFormat = false,
                                        showYearInDate = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - 24 Hour Format",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreen24HourPreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        timedEventTitle = "Lunch with Family",
                                        timedEventTime = LocalTime.of(12, 0),
                                        timedEventDate = null,
                                        use24HourFormat = true,
                                        showYearInDate = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - Long Event Title",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenLongTitlePreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        timedEventTitle =
                                                "Meet Eric downstairs so he can take you to your doctors appointment",
                                        timedEventTime = LocalTime.of(10, 0),
                                        timedEventDate = LocalDate.of(2026, 2, 23),
                                        use24HourFormat = false,
                                        showYearInDate = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - Tablet Large",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 1024,
        heightDp = 600
)
@Composable
private fun KioskScreenTabletPreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        allDayEvents = listOf(
                                                AllDayEventInfo(
                                                        title = "Company Retreat",
                                                        startDate = LocalDate.of(2026, 2, 21)
                                                )
                                        ),
                                        timedEventTitle = "Physical Therapy",
                                        timedEventTime = LocalTime.of(11, 0),
                                        timedEventDate = null,
                                        use24HourFormat = false,
                                        showYearInDate = false
                                )
                )
        }
}

// endregion
