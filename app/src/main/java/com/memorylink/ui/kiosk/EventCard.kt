package com.memorylink.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Displays the next calendar event with time and title as a single flowing sentence.
 *
 * Format:
 * - Timed events: "At 10:30 AM, Event Title"
 * - All-day events: "Today is Event Title"
 *
 * Text auto-sizes to fill the available space while wrapping naturally. Designed for
 * elderly/sight-challenged users - text is always as large as possible.
 *
 * @param title The event title to display
 * @param startTime The event start time, or null for all-day events
 * @param use24HourFormat Whether to use 24-hour format (default: false = 12-hour)
 * @param modifier Modifier for the root container (should include size constraints)
 */
@Composable
fun EventCard(
        title: String,
        startTime: LocalTime?,
        use24HourFormat: Boolean = false,
        modifier: Modifier = Modifier
) {
        // Combine time and title into a single sentence
        // Use non-breaking spaces (\u00A0) in "At {time}," so it won't wrap mid-phrase
        val displayText =
                if (startTime != null) {
                        val timeFormatter =
                                if (use24HourFormat) {
                                        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                                } else {
                                        DateTimeFormatter.ofPattern(
                                                "h:mm\u00A0a",
                                                Locale.getDefault()
                                        )
                                }
                        // Format time and normalize AM/PM to lowercase without periods
                        val formattedTime =
                                startTime
                                        .format(timeFormatter)
                                        .replace("AM", "am")
                                        .replace("PM", "pm")
                                        .replace("a.m.", "am")
                                        .replace("p.m.", "pm")
                        "At\u00A0$formattedTime, $title"
                } else {
                        "Today is $title"
                }

        Box(
                modifier =
                        modifier.clip(RoundedCornerShape(16.dp))
                                .background(DarkSurface)
                                .padding(24.dp),
                contentAlignment = Alignment.TopCenter
        ) {
                AutoSizeText(
                        text = displayText,
                        modifier = Modifier.fillMaxSize(),
                        style = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold),
                        maxFontSize = DisplayConstants.MAX_FONT_SIZE,
                        minFontSize = DisplayConstants.MIN_FONT_SIZE
                )
        }
}

// region Previews

@Preview(
        name = "Event Card - Short Title (Timed)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 300
)
@Composable
private fun EventCardShortTitlePreview() {
        MemoryLinkTheme {
                EventCard(
                        title = "Doctor",
                        startTime = LocalTime.of(10, 30),
                        use24HourFormat = false,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

@Preview(
        name = "Event Card - Medium Title (Timed)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 300
)
@Composable
private fun EventCardMediumTitlePreview() {
        MemoryLinkTheme {
                EventCard(
                        title = "Doctor Appointment",
                        startTime = LocalTime.of(10, 30),
                        use24HourFormat = false,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

@Preview(
        name = "Event Card - Long Title (Timed)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 300
)
@Composable
private fun EventCardLongTitlePreview() {
        MemoryLinkTheme {
                EventCard(
                        title = "Family Dinner at Sarah's House",
                        startTime = LocalTime.of(18, 0),
                        use24HourFormat = false,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

@Preview(
        name = "Event Card - Very Long Title (Timed)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 300
)
@Composable
private fun EventCardVeryLongTitlePreview() {
        MemoryLinkTheme {
                EventCard(
                        title =
                                "Meet Eric downstairs so he can take you to your doctors appointment",
                        startTime = LocalTime.of(15, 0),
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
                        use24HourFormat = true,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

@Preview(
        name = "Event Card - All-Day Event",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 300
)
@Composable
private fun EventCardAllDayPreview() {
        MemoryLinkTheme {
                EventCard(
                        title = "Mom's Birthday",
                        startTime = null,
                        use24HourFormat = false,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

@Preview(
        name = "Event Card - All-Day Long Title",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 300
)
@Composable
private fun EventCardAllDayLongPreview() {
        MemoryLinkTheme {
                EventCard(
                        title = "Family Reunion at the Park",
                        startTime = null,
                        use24HourFormat = false,
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
                        use24HourFormat = false,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                )
        }
}

// endregion
