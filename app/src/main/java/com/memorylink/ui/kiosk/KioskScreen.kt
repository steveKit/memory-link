package com.memorylink.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.memorylink.domain.model.DisplayState
import com.memorylink.ui.theme.DarkBackground
import com.memorylink.ui.theme.MemoryLinkTheme
import java.time.LocalDate
import java.time.LocalTime

/**
 * Main kiosk screen that renders the appropriate UI based on DisplayState.
 *
 * States:
 * - AwakeNoEvent: Clock centered, full brightness
 * - AwakeWithEvent: Clock (top) + EventCard (bottom), split by messageAreaPercent
 * - Sleep: Dimmed clock only via SleepOverlay
 *
 * Background: #121212 (DarkBackground)
 *
 * @param displayState The current display state to render
 * @param messageAreaPercent Percentage of screen height for event area (20-80, default 60)
 * @param modifier Modifier for the root Box
 */
@Composable
fun KioskScreen(
    displayState: DisplayState,
    messageAreaPercent: Int = 60,
    modifier: Modifier = Modifier
) {
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
                    eventTime = displayState.nextEventTime,
                    messageAreaPercent = messageAreaPercent
                )
            }
            is DisplayState.Sleep -> {
                SleepOverlay(
                    time = displayState.currentTime,
                    use24HourFormat = displayState.use24HourFormat,
                    visible = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Content displayed when awake with no events.
 * Clock is centered and fills the screen.
 */
@Composable
private fun AwakeNoEventContent(
    currentTime: LocalTime,
    currentDate: LocalDate,
    use24HourFormat: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        ClockDisplay(
            time = currentTime,
            date = currentDate,
            use24HourFormat = use24HourFormat,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Content displayed when awake with an event.
 * Screen is split: clock area on top, event card on bottom.
 *
 * @param messageAreaPercent Percentage of screen height for the event area
 */
@Composable
private fun AwakeWithEventContent(
    currentTime: LocalTime,
    currentDate: LocalDate,
    use24HourFormat: Boolean,
    eventTitle: String,
    eventTime: LocalTime?,
    messageAreaPercent: Int,
    modifier: Modifier = Modifier
) {
    // Ensure messageAreaPercent is within valid range
    val validPercent = messageAreaPercent.coerceIn(20, 80)
    val clockWeight = (100 - validPercent).toFloat()
    val eventWeight = validPercent.toFloat()

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Clock area - takes (100 - messageAreaPercent)% of height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(clockWeight),
            contentAlignment = Alignment.Center
        ) {
            ClockDisplay(
                time = currentTime,
                date = currentDate,
                use24HourFormat = use24HourFormat,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Event area - takes messageAreaPercent% of height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(eventWeight),
            contentAlignment = Alignment.Center
        ) {
            EventCard(
                title = eventTitle,
                startTime = eventTime,
                use24HourFormat = use24HourFormat,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// region Previews

@Preview(
    name = "Kiosk Screen - Awake No Event",
    showBackground = true,
    backgroundColor = 0xFF121212,
    widthDp = 800,
    heightDp = 480
)
@Composable
private fun KioskScreenAwakeNoEventPreview() {
    MemoryLinkTheme {
        KioskScreen(
            displayState = DisplayState.AwakeNoEvent(
                currentTime = LocalTime.of(10, 30),
                currentDate = LocalDate.of(2026, 2, 11),
                use24HourFormat = false
            )
        )
    }
}

@Preview(
    name = "Kiosk Screen - Awake With Event",
    showBackground = true,
    backgroundColor = 0xFF121212,
    widthDp = 800,
    heightDp = 480
)
@Composable
private fun KioskScreenAwakeWithEventPreview() {
    MemoryLinkTheme {
        KioskScreen(
            displayState = DisplayState.AwakeWithEvent(
                currentTime = LocalTime.of(9, 15),
                currentDate = LocalDate.of(2026, 2, 11),
                nextEventTitle = "Doctor Appointment",
                nextEventTime = LocalTime.of(10, 30),
                use24HourFormat = false
            ),
            messageAreaPercent = 60
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
            displayState = DisplayState.AwakeWithEvent(
                currentTime = LocalTime.of(10, 0),
                currentDate = LocalDate.of(2026, 2, 11),
                nextEventTitle = "Mom's Birthday",
                nextEventTime = null,
                use24HourFormat = false
            ),
            messageAreaPercent = 60
        )
    }
}

@Preview(
    name = "Kiosk Screen - Sleep Mode",
    showBackground = true,
    backgroundColor = 0xFF0A0A0A,
    widthDp = 800,
    heightDp = 480
)
@Composable
private fun KioskScreenSleepPreview() {
    MemoryLinkTheme {
        KioskScreen(
            displayState = DisplayState.Sleep(
                currentTime = LocalTime.of(22, 45),
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
            displayState = DisplayState.AwakeWithEvent(
                currentTime = LocalTime.of(14, 30),
                currentDate = LocalDate.of(2026, 2, 11),
                nextEventTitle = "Lunch with Family",
                nextEventTime = LocalTime.of(12, 0),
                use24HourFormat = true
            ),
            messageAreaPercent = 60
        )
    }
}

@Preview(
    name = "Kiosk Screen - Large Message Area (80%)",
    showBackground = true,
    backgroundColor = 0xFF121212,
    widthDp = 800,
    heightDp = 480
)
@Composable
private fun KioskScreenLargeMessageAreaPreview() {
    MemoryLinkTheme {
        KioskScreen(
            displayState = DisplayState.AwakeWithEvent(
                currentTime = LocalTime.of(9, 0),
                currentDate = LocalDate.of(2026, 2, 11),
                nextEventTitle = "Physical Therapy",
                nextEventTime = LocalTime.of(11, 0),
                use24HourFormat = false
            ),
            messageAreaPercent = 80
        )
    }
}

@Preview(
    name = "Kiosk Screen - Small Message Area (20%)",
    showBackground = true,
    backgroundColor = 0xFF121212,
    widthDp = 800,
    heightDp = 480
)
@Composable
private fun KioskScreenSmallMessageAreaPreview() {
    MemoryLinkTheme {
        KioskScreen(
            displayState = DisplayState.AwakeWithEvent(
                currentTime = LocalTime.of(9, 0),
                currentDate = LocalDate.of(2026, 2, 11),
                nextEventTitle = "Lunch",
                nextEventTime = LocalTime.of(12, 0),
                use24HourFormat = false
            ),
            messageAreaPercent = 20
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
            displayState = DisplayState.AwakeWithEvent(
                currentTime = LocalTime.of(9, 0),
                currentDate = LocalDate.of(2026, 2, 11),
                nextEventTitle = "Annual Family Reunion Potluck Dinner",
                nextEventTime = LocalTime.of(17, 0),
                use24HourFormat = false
            ),
            messageAreaPercent = 60
        )
    }
}

// endregion
