package com.memorylink.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.memorylink.ui.theme.DarkSurface
import com.memorylink.ui.theme.EventTimeStyle
import com.memorylink.ui.theme.EventTitleStyle
import com.memorylink.ui.theme.MemoryLinkTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Displays the next calendar event with title and start time.
 *
 * Typography:
 * - Title: 48sp bold (EventTitleStyle) - white
 * - Time: 32sp normal (EventTimeStyle) - accent blue (#90CAF9)
 *
 * @param title The event title to display
 * @param startTime The event start time
 * @param use24HourFormat Whether to use 24-hour format (default: false = 12-hour)
 * @param modifier Modifier for the root Card
 */
@Composable
fun EventCard(
    title: String,
    startTime: LocalTime,
    use24HourFormat: Boolean = false,
    modifier: Modifier = Modifier
) {
    val timeFormatter = if (use24HourFormat) {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    } else {
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = EventTitleStyle,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = startTime.format(timeFormatter),
            style = EventTimeStyle,
            textAlign = TextAlign.Center
        )
    }
}

// region Previews

@Preview(
    name = "Event Card - Short Title",
    showBackground = true,
    backgroundColor = 0xFF121212
)
@Composable
private fun EventCardShortTitlePreview() {
    MemoryLinkTheme {
        EventCard(
            title = "Doctor Appointment",
            startTime = LocalTime.of(10, 30),
            use24HourFormat = false,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(
    name = "Event Card - Long Title",
    showBackground = true,
    backgroundColor = 0xFF121212
)
@Composable
private fun EventCardLongTitlePreview() {
    MemoryLinkTheme {
        EventCard(
            title = "Family Dinner at Sarah's House",
            startTime = LocalTime.of(18, 0),
            use24HourFormat = false,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(
    name = "Event Card - 24 Hour Format",
    showBackground = true,
    backgroundColor = 0xFF121212
)
@Composable
private fun EventCard24HourPreview() {
    MemoryLinkTheme {
        EventCard(
            title = "Lunch",
            startTime = LocalTime.of(12, 0),
            use24HourFormat = true,
            modifier = Modifier.padding(16.dp)
        )
    }
}

// endregion
