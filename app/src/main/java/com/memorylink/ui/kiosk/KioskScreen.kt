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
 * - AwakeWithEvent: Clock + date (smaller) + EventCard, all centered
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
                                        eventTitle = displayState.nextEventTitle,
                                        eventTime = displayState.nextEventTime
                                )
                        }
                        is DisplayState.Sleep -> {
                                SleepContent(
                                        currentTime = currentTime,
                                        currentDate = currentDate,
                                        use24HourFormat = displayState.use24HourFormat,
                                        showYearInDate = displayState.showYearInDate
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
        showYearInDate: Boolean = true,
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
 * Content displayed when awake with an event. Uses layered layout with dynamic background:
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
        showYearInDate: Boolean = true,
        eventTitle: String,
        eventTime: LocalTime?,
        modifier: Modifier = Modifier
) {
        var eventCardYOffset by remember { mutableFloatStateOf(0f) }

        Box(modifier = modifier.fillMaxSize()) {
                // Background layer: edge-to-edge, positioned at EventCard's Y position, extends to
                // bottom
                if (eventCardYOffset > 0f) {
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
                        Box(contentAlignment = Alignment.BottomCenter) {
                                ClockDisplay(
                                        time = currentTime,
                                        date = currentDate,
                                        use24HourFormat = use24HourFormat,
                                        showYearInDate = showYearInDate,
                                        colorScheme = ClockColorScheme.Awake,
                                        modifier = Modifier.fillMaxWidth()
                                )
                        }

                        Spacer(modifier = Modifier.height(DisplayConstants.CLOCK_TO_EVENT_SPACING))

                        // Event text - top justified, tracks its position for background layer
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth().onGloballyPositioned { coords ->
                                                eventCardYOffset = coords.positionInParent().y
                                        },
                                contentAlignment = Alignment.TopStart
                        ) {
                                EventCard(
                                        title = eventTitle,
                                        startTime = eventTime,
                                        use24HourFormat = use24HourFormat,
                                        showBackground = false,
                                        modifier = Modifier.fillMaxWidth()
                                )
                        }

                        // Bottom spacer - creates equal margin below event (matches top)
                        Spacer(modifier = Modifier.weight(1f))
                }
        }
}

/**
 * Content displayed during sleep mode. Same layout as AwakeNoEvent but with dimmed colors and
 * darker background. Uses 1-second cross-fade animation when entering/exiting.
 */
@Composable
private fun SleepContent(
        currentTime: LocalTime,
        currentDate: LocalDate,
        use24HourFormat: Boolean,
        showYearInDate: Boolean = true,
        modifier: Modifier = Modifier
) {
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
                KioskScreen(displayState = DisplayState.AwakeNoEvent(use24HourFormat = false))
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
                KioskScreen(displayState = DisplayState.AwakeNoEvent(use24HourFormat = false))
        }
}

@Preview(
        name = "Kiosk Screen - Awake With Event (Landscape)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenAwakeWithEventPreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        nextEventTitle = "Doctor Appointment",
                                        nextEventTime = LocalTime.of(10, 30),
                                        use24HourFormat = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - Awake With Event (Portrait)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 480,
        heightDp = 800
)
@Composable
private fun KioskScreenAwakeWithEventPortraitPreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        nextEventTitle = "Doctor Appointment",
                                        nextEventTime = LocalTime.of(10, 30),
                                        use24HourFormat = false
                                )
                )
        }
}

@Preview(
        name = "Kiosk Screen - All-Day Event",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun KioskScreenAllDayEventPreview() {
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.AwakeWithEvent(
                                        nextEventTitle = "Mom's Birthday",
                                        nextEventTime = null,
                                        use24HourFormat = false
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
        MemoryLinkTheme { KioskScreen(displayState = DisplayState.Sleep(use24HourFormat = false)) }
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
        MemoryLinkTheme { KioskScreen(displayState = DisplayState.Sleep(use24HourFormat = false)) }
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
                                        nextEventTitle = "Lunch with Family",
                                        nextEventTime = LocalTime.of(12, 0),
                                        use24HourFormat = true
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
                                        nextEventTitle =
                                                "Meet Eric downstairs so he can take you to your doctors appointment",
                                        nextEventTime = LocalTime.of(10, 0),
                                        use24HourFormat = false
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
                                        nextEventTitle = "Physical Therapy",
                                        nextEventTime = LocalTime.of(11, 0),
                                        use24HourFormat = false
                                )
                )
        }
}

// endregion
