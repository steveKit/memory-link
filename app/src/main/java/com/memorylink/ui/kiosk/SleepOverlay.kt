package com.memorylink.ui.kiosk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.memorylink.ui.theme.MemoryLinkTheme
import com.memorylink.ui.theme.SleepBackground
import com.memorylink.ui.theme.SleepClockStyle
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sleep mode overlay displaying only a dimmed clock.
 *
 * Typography:
 * - Clock: 48sp bold in muted gray (SleepClockStyle)
 *
 * Background: #0A0A0A (darker than normal mode)
 * No date, no events displayed during sleep mode.
 *
 * Uses 1-second cross-fade animation when entering/exiting (per NFR-02).
 *
 * @param time The current time to display
 * @param use24HourFormat Whether to use 24-hour format (default: false = 12-hour)
 * @param visible Whether the overlay should be visible (controls animation)
 * @param modifier Modifier for the root Box
 */
@Composable
fun SleepOverlay(
    time: LocalTime,
    use24HourFormat: Boolean = false,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val timeFormatter = if (use24HourFormat) {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    } else {
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 1000)),
        exit = fadeOut(animationSpec = tween(durationMillis = 1000)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SleepBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = time.format(timeFormatter),
                style = SleepClockStyle,
                textAlign = TextAlign.Center
            )
        }
    }
}

// region Previews

@Preview(
    name = "Sleep Overlay - 12 Hour",
    showBackground = true,
    backgroundColor = 0xFF0A0A0A,
    widthDp = 800,
    heightDp = 480
)
@Composable
private fun SleepOverlay12HourPreview() {
    MemoryLinkTheme {
        SleepOverlay(
            time = LocalTime.of(23, 45),
            use24HourFormat = false
        )
    }
}

@Preview(
    name = "Sleep Overlay - 24 Hour",
    showBackground = true,
    backgroundColor = 0xFF0A0A0A,
    widthDp = 800,
    heightDp = 480
)
@Composable
private fun SleepOverlay24HourPreview() {
    MemoryLinkTheme {
        SleepOverlay(
            time = LocalTime.of(23, 45),
            use24HourFormat = true
        )
    }
}

@Preview(
    name = "Sleep Overlay - Early Morning",
    showBackground = true,
    backgroundColor = 0xFF0A0A0A,
    widthDp = 800,
    heightDp = 480
)
@Composable
private fun SleepOverlayEarlyMorningPreview() {
    MemoryLinkTheme {
        SleepOverlay(
            time = LocalTime.of(3, 30),
            use24HourFormat = false
        )
    }
}

// endregion
