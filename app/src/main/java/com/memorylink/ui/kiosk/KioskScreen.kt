package com.memorylink.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
 * - AwakeNoEvent: Clock + Date, full brightness
 * - AwakeWithEvent: Clock + Date + EventCard, full brightness
 * - Sleep: Dimmed clock only via SleepOverlay
 *
 * Background: #121212 (DarkBackground)
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
                AwakeContent(
                        currentTime = displayState.currentTime,
                        currentDate = displayState.currentDate,
                        use24HourFormat = displayState.use24HourFormat,
                        eventTitle = null,
                        eventTime = null
                )
            }
            is DisplayState.AwakeWithEvent -> {
                AwakeContent(
                        currentTime = displayState.currentTime,
                        currentDate = displayState.currentDate,
                        use24HourFormat = displayState.use24HourFormat,
                        eventTitle = displayState.nextEventTitle,
                        eventTime = displayState.nextEventTime
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

/** Content displayed during awake states (with or without event). */
@Composable
private fun AwakeContent(
        currentTime: LocalTime,
        currentDate: LocalDate,
        use24HourFormat: Boolean,
        eventTitle: String?,
        eventTime: LocalTime?,
        modifier: Modifier = Modifier
) {
    Column(
            modifier = modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        ClockDisplay(time = currentTime, date = currentDate, use24HourFormat = use24HourFormat)

        if (eventTitle != null && eventTime != null) {
            Spacer(modifier = Modifier.height(48.dp))

            EventCard(
                    title = eventTitle,
                    startTime = eventTime,
                    use24HourFormat = use24HourFormat,
                    modifier = Modifier.padding(horizontal = 32.dp)
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
                displayState =
                        DisplayState.Sleep(
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

// endregion
