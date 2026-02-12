package com.memorylink.ui.kiosk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.memorylink.ui.components.AutoSizeText
import com.memorylink.ui.theme.MemoryLinkTheme
import com.memorylink.ui.theme.TextPrimary
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Displays the current time and date with auto-sizing text.
 *
 * Text is maximized to fill the available space while maintaining readability. The time takes
 * approximately 70% of the height, date takes 30%. Both are centered vertically and horizontally
 * within the container.
 *
 * Date format: "Wednesday, February 11, 2026" (full names with year) Time format: Configurable
 * 12-hour (h:mm a) or 24-hour (HH:mm)
 *
 * @param time The current time to display
 * @param date The current date to display
 * @param use24HourFormat Whether to use 24-hour format (default: false = 12-hour)
 * @param modifier Modifier for the root Column
 */
@Composable
fun ClockDisplay(
        time: LocalTime,
        date: LocalDate,
        use24HourFormat: Boolean = false,
        modifier: Modifier = Modifier
) {
    val timeFormatter =
            if (use24HourFormat) {
                DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            } else {
                DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            }

    // Full date format: "Wednesday, February 11, 2026"
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())

    Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        // Time - takes 70% of height, auto-sizes to fill
        AutoSizeText(
                text = time.format(timeFormatter),
                modifier = Modifier.fillMaxWidth().weight(0.7f),
                style = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold),
                maxFontSize = 400.sp,
                minFontSize = 48.sp
        )

        // Date - takes 30% of height, auto-sizes to fill
        AutoSizeText(
                text = date.format(dateFormatter),
                modifier = Modifier.fillMaxWidth().weight(0.3f),
                style = TextStyle(color = TextPrimary, fontWeight = FontWeight.Normal),
                maxFontSize = 200.sp,
                minFontSize = 24.sp
        )
    }
}

// region Previews

@Preview(
        name = "Clock Display - 12 Hour",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 400
)
@Composable
private fun ClockDisplay12HourPreview() {
    MemoryLinkTheme {
        ClockDisplay(
                time = LocalTime.of(14, 30),
                date = LocalDate.of(2026, 2, 11),
                use24HourFormat = false,
                modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(
        name = "Clock Display - 24 Hour",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 400
)
@Composable
private fun ClockDisplay24HourPreview() {
    MemoryLinkTheme {
        ClockDisplay(
                time = LocalTime.of(14, 30),
                date = LocalDate.of(2026, 2, 11),
                use24HourFormat = true,
                modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(
        name = "Clock Display - Morning",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 400
)
@Composable
private fun ClockDisplayMorningPreview() {
    MemoryLinkTheme {
        ClockDisplay(
                time = LocalTime.of(9, 5),
                date = LocalDate.of(2026, 12, 25),
                use24HourFormat = false,
                modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(
        name = "Clock Display - Tablet Landscape",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 1024,
        heightDp = 600
)
@Composable
private fun ClockDisplayTabletPreview() {
    MemoryLinkTheme {
        ClockDisplay(
                time = LocalTime.of(10, 30),
                date = LocalDate.of(2026, 2, 11),
                use24HourFormat = false,
                modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(
        name = "Clock Display - Small Area (with event)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 200
)
@Composable
private fun ClockDisplaySmallAreaPreview() {
    MemoryLinkTheme {
        ClockDisplay(
                time = LocalTime.of(3, 45),
                date = LocalDate.of(2026, 2, 11),
                use24HourFormat = false,
                modifier = Modifier.fillMaxSize()
        )
    }
}

// endregion
