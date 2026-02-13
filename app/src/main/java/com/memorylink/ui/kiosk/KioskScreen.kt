package com.memorylink.ui.kiosk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.memorylink.domain.model.DisplayState
import com.memorylink.ui.theme.DarkBackground
import com.memorylink.ui.theme.DarkSurface
import com.memorylink.ui.theme.DisplayConstants
import com.memorylink.ui.theme.MemoryLinkTheme
import com.memorylink.ui.theme.SleepBackground
import java.time.LocalDate
import java.time.LocalTime

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
 *
 * @param displayState The current display state to render
 * @param modifier Modifier for the root Box
 */
@Composable
fun KioskScreen(displayState: DisplayState, modifier: Modifier = Modifier) {
        Box(
                modifier = modifier.fillMaxSize().background(DarkBackground),
                contentAlignment = Alignment.Center
        ) {
                when (displayState) {
                        is DisplayState.AwakeNoEvent -> {
                                AwakeNoEventContent(
                                        currentTime = displayState.currentTime,
                                        currentDate = displayState.currentDate,
                                        use24HourFormat = displayState.use24HourFormat
                                )
                        }
                        is DisplayState.AwakeWithEvent -> {
                                AwakeWithEventContent(
                                        currentTime = displayState.currentTime,
                                        currentDate = displayState.currentDate,
                                        use24HourFormat = displayState.use24HourFormat,
                                        eventTitle = displayState.nextEventTitle,
                                        eventTime = displayState.nextEventTime
                                )
                        }
                        is DisplayState.Sleep -> {
                                SleepContent(
                                        currentTime = displayState.currentTime,
                                        currentDate = displayState.currentDate,
                                        use24HourFormat = displayState.use24HourFormat
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
                        colorScheme = ClockColorScheme.Awake,
                        modifier = Modifier.fillMaxSize()
                )
        }
}

/**
 * Content displayed when awake with an event. Uses layered layout:
 * - Background layer: DarkSurface area extending from below clock to bottom
 * - Content layer: Clock + event text centered as a group
 *
 * This creates equal visual margin above clock and below event text while the message background
 * extends to the bottom of the screen.
 */
@Composable
private fun AwakeWithEventContent(
        currentTime: LocalTime,
        currentDate: LocalDate,
        use24HourFormat: Boolean,
        eventTitle: String,
        eventTime: LocalTime?,
        modifier: Modifier = Modifier
) {
        Box(modifier = modifier.fillMaxSize().padding(DisplayConstants.SCREEN_MARGIN)) {
                // Background layer: message area color extending from ~50% to bottom
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .fillMaxHeight(0.55f)
                                        .align(Alignment.BottomCenter)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(DarkSurface)
                )

                // Content layer: centered clock + event text (no background on EventCard)
                Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        // Clock area
                        ClockDisplay(
                                time = currentTime,
                                date = currentDate,
                                use24HourFormat = use24HourFormat,
                                colorScheme = ClockColorScheme.Awake,
                                modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(DisplayConstants.CLOCK_TO_EVENT_SPACING))

                        // Event text (transparent background - bg provided by layer behind)
                        EventCard(
                                title = eventTitle,
                                startTime = eventTime,
                                use24HourFormat = use24HourFormat,
                                showBackground = false,
                                modifier = Modifier.fillMaxWidth()
                        )
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
                KioskScreen(
                        displayState =
                                DisplayState.AwakeNoEvent(
                                        currentTime = LocalTime.of(10, 30),
                                        currentDate = LocalDate.of(2026, 2, 11),
                                        use24HourFormat = false
                                )
                )
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
                KioskScreen(
                        displayState =
                                DisplayState.AwakeNoEvent(
                                        currentTime = LocalTime.of(10, 30),
                                        currentDate = LocalDate.of(2026, 2, 11),
                                        use24HourFormat = false
                                )
                )
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
                                        currentTime = LocalTime.of(9, 15),
                                        currentDate = LocalDate.of(2026, 2, 11),
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
                                        currentTime = LocalTime.of(9, 15),
                                        currentDate = LocalDate.of(2026, 2, 11),
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
                                        currentTime = LocalTime.of(10, 0),
                                        currentDate = LocalDate.of(2026, 2, 11),
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
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.Sleep(
                                        currentTime = LocalTime.of(22, 45),
                                        currentDate = LocalDate.of(2026, 2, 11),
                                        use24HourFormat = false
                                )
                )
        }
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
        MemoryLinkTheme {
                KioskScreen(
                        displayState =
                                DisplayState.Sleep(
                                        currentTime = LocalTime.of(22, 45),
                                        currentDate = LocalDate.of(2026, 2, 11),
                                        use24HourFormat = false
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
                                        currentTime = LocalTime.of(14, 30),
                                        currentDate = LocalDate.of(2026, 2, 11),
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
                                        currentTime = LocalTime.of(9, 0),
                                        currentDate = LocalDate.of(2026, 2, 11),
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
                                        currentTime = LocalTime.of(10, 30),
                                        currentDate = LocalDate.of(2026, 2, 11),
                                        nextEventTitle = "Physical Therapy",
                                        nextEventTime = LocalTime.of(11, 0),
                                        use24HourFormat = false
                                )
                )
        }
}

// endregion
