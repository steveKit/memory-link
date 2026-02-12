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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
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
 * Displays the next calendar event with title and start time.
 *
 * The title uses auto-sizing text that:
 * - Starts at maximum font size (64sp)
 * - Shrinks to fit the available width
 * - Wraps to maximum 3 lines
 * - Has a minimum size of 24sp for accessibility
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
    val timeFormatter =
            if (use24HourFormat) {
                DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            } else {
                DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            }

    Column(
            modifier =
                    modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurface)
                            .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Auto-sizing title
        AutoSizeText(
                text = title,
                maxFontSize = 64.sp,
                minFontSize = 24.sp,
                maxLines = 3,
                style = TextStyle(fontWeight = FontWeight.Bold, color = TextPrimary),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Auto-sizing time
        AutoSizeText(
                text = startTime.format(timeFormatter),
                maxFontSize = 48.sp,
                minFontSize = 20.sp,
                maxLines = 1,
                style = TextStyle(fontWeight = FontWeight.Normal, color = AccentBlue),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Text composable that automatically adjusts font size to fit within its bounds.
 *
 * Starts at maxFontSize and shrinks until text fits, respecting minFontSize.
 *
 * @param text The text to display
 * @param maxFontSize Maximum font size to try
 * @param minFontSize Minimum font size (accessibility floor)
 * @param maxLines Maximum lines before overflow
 * @param style Base text style (font size will be overridden)
 * @param textAlign Text alignment
 * @param modifier Modifier for the text
 */
@Composable
fun AutoSizeText(
        text: String,
        maxFontSize: TextUnit,
        minFontSize: TextUnit,
        maxLines: Int,
        style: TextStyle,
        textAlign: TextAlign,
        modifier: Modifier = Modifier
) {
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
            text = text,
            style = style.copy(fontSize = fontSize, lineHeight = fontSize * 1.2f),
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            modifier =
                    modifier.drawWithContent {
                        if (readyToDraw) {
                            drawContent()
                        }
                    },
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.didOverflowHeight || textLayoutResult.didOverflowWidth) {
                    // Text overflowed, try smaller font
                    val newSize = fontSize * 0.9f
                    if (newSize >= minFontSize) {
                        fontSize = newSize
                    } else {
                        // Can't shrink more, just show what fits
                        fontSize = minFontSize
                        readyToDraw = true
                    }
                } else {
                    // Text fits, we're done
                    readyToDraw = true
                }
            }
    )
}

// region Previews

@Preview(
        name = "Event Card - Short Title",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400
)
@Composable
private fun EventCardShortTitlePreview() {
    MemoryLinkTheme {
        EventCard(
                title = "Doctor",
                startTime = LocalTime.of(10, 30),
                use24HourFormat = false,
                modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(
        name = "Event Card - Medium Title",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400
)
@Composable
private fun EventCardMediumTitlePreview() {
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
        backgroundColor = 0xFF121212,
        widthDp = 400
)
@Composable
private fun EventCardLongTitlePreview() {
    MemoryLinkTheme {
        EventCard(
                title = "Family Dinner at Sarah's House with Everyone",
                startTime = LocalTime.of(18, 0),
                use24HourFormat = false,
                modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(
        name = "Event Card - Very Long Title",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400
)
@Composable
private fun EventCardVeryLongTitlePreview() {
    MemoryLinkTheme {
        EventCard(
                title =
                        "Annual Family Reunion Potluck Dinner at Grandma's House - Bring a Dish to Share",
                startTime = LocalTime.of(17, 0),
                use24HourFormat = false,
                modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(
        name = "Event Card - 24 Hour Format",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400
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

@Preview(
        name = "Event Card - Narrow Width",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 250
)
@Composable
private fun EventCardNarrowPreview() {
    MemoryLinkTheme {
        EventCard(
                title = "Physical Therapy Session",
                startTime = LocalTime.of(14, 30),
                use24HourFormat = false,
                modifier = Modifier.padding(16.dp)
        )
    }
}

// endregion
