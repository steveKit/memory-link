package com.memorylink.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.memorylink.ui.components.AutoSizeText
import com.memorylink.ui.theme.DarkSurface
import com.memorylink.ui.theme.DisplayConstants
import com.memorylink.ui.theme.MemoryLinkTheme
import com.memorylink.ui.theme.TextPrimary
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Displays the next calendar event with time and title as a single flowing sentence.
 *
 * Format:
 * - Timed event today: "At 10:30 am, Event Title"
 * - Timed event tomorrow: "Tomorrow, at 10:30 am, Event Title"
 * - Timed event future: "On Wednesday, February 18 at 10:30 am, Event Title"
 *
 * Note: Year is never shown in the event card since it's already displayed in the main date area.
 *
 * Text auto-sizes to fill the available space while wrapping naturally. Designed for
 * elderly/sight-challenged users - text is always as large as possible.
 *
 * @param title The event title to display
 * @param startTime The event start time
 * @param eventDate The event date, or null if the event is today
 * @param use24HourFormat Whether to use 24-hour format (default: false = 12-hour)
 * @param showBackground Whether to show the DarkSurface background (default: true)
 * @param modifier Modifier for the root container (should include size constraints)
 */
@Composable
fun EventCard(
        title: String,
        startTime: LocalTime,
        eventDate: LocalDate? = null,
        use24HourFormat: Boolean = false,
        showBackground: Boolean = true,
        modifier: Modifier = Modifier
) {
        // Format time
        val timeFormatter =
                if (use24HourFormat) {
                        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                } else {
                        DateTimeFormatter.ofPattern("h:mm\u00A0a", Locale.getDefault())
                }

        // Format time and normalize AM/PM to lowercase without periods
        val formattedTime =
                startTime
                        .format(timeFormatter)
                        .replace("AM", "am")
                        .replace("PM", "pm")
                        .replace("a.m.", "am")
                        .replace("p.m.", "pm")

        // Build display text based on whether event is today, tomorrow, or further future
        val tomorrow = LocalDate.now().plusDays(1)
        val displayText =
                when {
                        eventDate == null -> {
                                // Today: "At {time}, {title}"
                                // Use non-breaking spaces in "At {time}," so it won't wrap
                                // mid-phrase
                                "At $formattedTime, $title"
                        }
                        eventDate == tomorrow -> {
                                // Tomorrow: "Tomorrow, at {time}, {title}"
                                "Tomorrow, at $formattedTime, $title"
                        }
                        else -> {
                                // Future date: "On {day}, {date} at {time}, {title}"
                                // Year is never shown here - it's already in the main date area
                                val dateFormatter =
                                        DateTimeFormatter.ofPattern(
                                                "EEEE, MMMM d",
                                                Locale.getDefault()
                                        )
                                val formattedDate = eventDate.format(dateFormatter)
                                "On $formattedDate at $formattedTime, $title"
                        }
                }

        val backgroundModifier =
                if (showBackground) {
                        modifier.clip(RoundedCornerShape(16.dp))
                                .background(DarkSurface)
                                .padding(24.dp)
                } else {
                        modifier.padding(24.dp)
                }

        Box(modifier = backgroundModifier, contentAlignment = Alignment.TopStart) {
                AutoSizeText(
                        text = displayText,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold),
                        maxFontSize = DisplayConstants.MAX_FONT_SIZE,
                        minFontSize = DisplayConstants.MIN_FONT_SIZE,
                        contentAlignment = Alignment.TopStart
                )
        }
}

// region Previews

@Preview(
        name = "Event Card - Today Event",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 300
)
@Composable
private fun EventCardTodayPreview() {
        MemoryLinkTheme {
                EventCard(
                        title = "Doctor Appointment",
                        startTime = LocalTime.of(10, 30),
                        eventDate = null, // null = today
                        use24HourFormat = false,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

@Preview(
        name = "Event Card - Tomorrow Event",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 300
)
@Composable
private fun EventCardTomorrowPreview() {
        MemoryLinkTheme {
                EventCard(
                        title = "Grocery Shopping",
                        startTime = LocalTime.of(11, 0),
                        eventDate = LocalDate.now().plusDays(1), // tomorrow
                        use24HourFormat = false,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

@Preview(
        name = "Event Card - Future Event",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 300
)
@Composable
private fun EventCardFuturePreview() {
        MemoryLinkTheme {
                EventCard(
                        title = "Doctor Appointment",
                        startTime = LocalTime.of(10, 30),
                        eventDate = LocalDate.of(2026, 2, 19),
                        use24HourFormat = false,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

@Preview(
        name = "Event Card - Long Future Event",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 300
)
@Composable
private fun EventCardLongFuturePreview() {
        MemoryLinkTheme {
                EventCard(
                        title =
                                "Meet Eric downstairs so he can take you to your doctors appointment",
                        startTime = LocalTime.of(15, 0),
                        eventDate = LocalDate.of(2026, 2, 23),
                        use24HourFormat = false,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

@Preview(
        name = "Event Card - 24 Hour Format",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 300
)
@Composable
private fun EventCard24HourPreview() {
        MemoryLinkTheme {
                EventCard(
                        title = "Lunch",
                        startTime = LocalTime.of(12, 0),
                        eventDate = null,
                        use24HourFormat = true,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

@Preview(
        name = "Event Card - Tablet Landscape",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 700,
        heightDp = 350
)
@Composable
private fun EventCardTabletPreview() {
        MemoryLinkTheme {
                EventCard(
                        title = "Physical Therapy Session",
                        startTime = LocalTime.of(14, 30),
                        eventDate = LocalDate.of(2026, 2, 20),
                        use24HourFormat = false,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

@Preview(
        name = "Event Card - Small Area (20% of screen)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 100
)
@Composable
private fun EventCardSmallAreaPreview() {
        MemoryLinkTheme {
                EventCard(
                        title = "Lunch",
                        startTime = LocalTime.of(12, 0),
                        eventDate = null,
                        use24HourFormat = false,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                )
        }
}

@Preview(
        name = "Event Card - Large Area (80% of screen)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 400
)
@Composable
private fun EventCardLargeAreaPreview() {
        MemoryLinkTheme {
                EventCard(
                        title = "Doctor Appointment",
                        startTime = LocalTime.of(10, 30),
                        eventDate = null,
                        use24HourFormat = false,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

// endregion
