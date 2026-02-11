package com.memorylink.ui.kiosk

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.memorylink.ui.theme.ClockStyle
import com.memorylink.ui.theme.DateStyle
import com.memorylink.ui.theme.MemoryLinkTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Displays the current time and date in large, high-contrast text.
 *
 * Typography:
 * - Time: 72sp bold (ClockStyle)
 * - Date: 36sp normal (DateStyle)
 *
 * Date format: "Wednesday, February 11, 2026" (full names with year)
 * Time format: Configurable 12-hour (h:mm a) or 24-hour (HH:mm)
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
    val timeFormatter = if (use24HourFormat) {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    } else {
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    }

    // Full date format: "Wednesday, February 11, 2026"
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = time.format(timeFormatter),
            style = ClockStyle,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = date.format(dateFormatter),
            style = DateStyle,
            textAlign = TextAlign.Center
        )
    }
}

// region Previews

@Preview(
    name = "Clock Display - 12 Hour",
    showBackground = true,
    backgroundColor = 0xFF121212
)
@Composable
private fun ClockDisplay12HourPreview() {
    MemoryLinkTheme {
        ClockDisplay(
            time = LocalTime.of(14, 30),
            date = LocalDate.of(2026, 2, 11),
            use24HourFormat = false
        )
    }
}

@Preview(
    name = "Clock Display - 24 Hour",
    showBackground = true,
    backgroundColor = 0xFF121212
)
@Composable
private fun ClockDisplay24HourPreview() {
    MemoryLinkTheme {
        ClockDisplay(
            time = LocalTime.of(14, 30),
            date = LocalDate.of(2026, 2, 11),
            use24HourFormat = true
        )
    }
}

@Preview(
    name = "Clock Display - Morning",
    showBackground = true,
    backgroundColor = 0xFF121212
)
@Composable
private fun ClockDisplayMorningPreview() {
    MemoryLinkTheme {
        ClockDisplay(
            time = LocalTime.of(9, 5),
            date = LocalDate.of(2026, 12, 25),
            use24HourFormat = false
        )
    }
}

// endregion
