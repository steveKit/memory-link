package com.memorylink.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memorylink.ui.theme.AccentBlue
import com.memorylink.ui.theme.DarkSurface
import com.memorylink.ui.theme.MemoryLinkTheme
import com.memorylink.ui.theme.TextPrimary
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Displays the next calendar event with time prefix and title.
 *
 * Layout (time on top):
 * - Timed events: "AT 10:30 AM" / "Event Title"
 * - All-day events: "TODAY IS" / "Event Title"
 *
 * Both lines use the same font size and auto-scale together to fill the available space while never
 * truncating or using ellipsis.
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
    val timePrefix =
            if (startTime != null) {
                val timeFormatter =
                        if (use24HourFormat) {
                            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                        } else {
                            DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                        }
                "AT ${startTime.format(timeFormatter)}"
            } else {
                "TODAY IS"
            }

    Box(
            modifier =
                    modifier.clip(RoundedCornerShape(16.dp)).background(DarkSurface).padding(24.dp),
            contentAlignment = Alignment.Center
    ) {
        AutoSizeEventText(timePrefix = timePrefix, title = title, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Auto-sizing text component that displays time prefix and title.
 *
 * Both lines scale together to be as large as possible while fitting within the available space. No
 * truncation or ellipsis - text always displays completely.
 *
 * @param timePrefix The time line (e.g., "AT 10:30 AM" or "TODAY IS")
 * @param title The event title
 * @param modifier Modifier for the container
 */
@Composable
private fun AutoSizeEventText(timePrefix: String, title: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current

        // Calculate available space
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }

        // Start with a large font size and scale down
        val maxFontSizeSp = 80f
        val minFontSizeSp = 16f

        var fontSizeSp by
                remember(timePrefix, title, maxWidthPx, maxHeightPx) {
                    mutableFloatStateOf(maxFontSizeSp)
                }
        var readyToDraw by
                remember(timePrefix, title, maxWidthPx, maxHeightPx) { mutableStateOf(false) }

        Column(
                modifier =
                        Modifier.fillMaxWidth().drawWithContent {
                            if (readyToDraw) {
                                drawContent()
                            }
                        },
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Time prefix line
            Text(
                    text = timePrefix,
                    style =
                            TextStyle(
                                    fontSize = fontSizeSp.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentBlue,
                                    lineHeight = (fontSizeSp * 1.2f).sp
                            ),
                    textAlign = TextAlign.Center,
                    softWrap = true,
                    onTextLayout = { result ->
                        if (!readyToDraw) {
                            if (result.didOverflowWidth || result.didOverflowHeight) {
                                val newSize = fontSizeSp * 0.9f
                                if (newSize >= minFontSizeSp) {
                                    fontSizeSp = newSize
                                } else {
                                    fontSizeSp = minFontSizeSp
                                    readyToDraw = true
                                }
                            }
                        }
                    }
            )

            // Title line
            Text(
                    text = title,
                    style =
                            TextStyle(
                                    fontSize = fontSizeSp.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    lineHeight = (fontSizeSp * 1.2f).sp
                            ),
                    textAlign = TextAlign.Center,
                    softWrap = true,
                    onTextLayout = { result ->
                        if (!readyToDraw) {
                            if (result.didOverflowWidth || result.didOverflowHeight) {
                                val newSize = fontSizeSp * 0.9f
                                if (newSize >= minFontSizeSp) {
                                    fontSizeSp = newSize
                                } else {
                                    fontSizeSp = minFontSizeSp
                                    readyToDraw = true
                                }
                            } else {
                                // Both texts fit, we're done
                                readyToDraw = true
                            }
                        }
                    }
            )
        }
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
                title = "Annual Family Reunion Potluck Dinner at Grandma's House",
                startTime = LocalTime.of(17, 0),
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
        heightDp = 250
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

// endregion
