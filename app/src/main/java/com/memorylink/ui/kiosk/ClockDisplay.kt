package com.memorylink.ui.kiosk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.memorylink.ui.theme.AccentBlue
import com.memorylink.ui.theme.DisplayConstants
import com.memorylink.ui.theme.MemoryLinkTheme
import com.memorylink.ui.theme.SleepText
import com.memorylink.ui.theme.TextDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Color scheme for the clock display.
 *
 * Allows the same ClockDisplay composable to be used for both awake and sleep modes by passing
 * different color configurations.
 */
data class ClockColorScheme(val timeColor: Color, val dateColor: Color) {
        companion object {
                /** Bright colors for awake/daytime mode. */
                val Awake = ClockColorScheme(timeColor = AccentBlue, dateColor = TextDate)

                /** Dimmed colors for sleep mode. */
                val Sleep = ClockColorScheme(timeColor = SleepText, dateColor = SleepText)
        }
}

/**
 * Displays the current time and date, centered as a unit.
 *
 * Layout behavior:
 * - Time uses MAX_FONT_SIZE (or smaller if it doesn't fit width as single line)
 * - Date independently sizes to fit full width as a single line (never wraps)
 * - Both sizes are calculated independently for optimal readability
 *
 * The time and date are positioned immediately adjacent (no artificial spacing from weights). Text
 * auto-sizes to fill available space while respecting max font size limits.
 *
 * This component is used for both AwakeNoEvent and Sleep display states.
 *
 * @param time The current time to display
 * @param date The current date to display
 * @param use24HourFormat Whether to use 24-hour format (default: false = 12-hour)
 * @param colorScheme Colors for time and date text
 * @param modifier Modifier for the root container
 */
@Composable
fun ClockDisplay(
        time: LocalTime,
        date: LocalDate,
        use24HourFormat: Boolean = false,
        colorScheme: ClockColorScheme = ClockColorScheme.Awake,
        modifier: Modifier = Modifier
) {
        val timeFormatter =
                if (use24HourFormat) {
                        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                } else {
                        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                }

        // Normalize AM/PM to lowercase without periods
        val formattedTime =
                time.format(timeFormatter)
                        .replace("AM", "am")
                        .replace("PM", "pm")
                        .replace("a.m.", "am")
                        .replace("p.m.", "pm")

        // Full date format: "Wednesday, February 11, 2026"
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
        val formattedDate = date.format(dateFormatter)

        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current

                val maxWidthPx = constraints.maxWidth

                // Calculate time font size: MAX_FONT_SIZE or smaller to fit width
                val timeFontSize =
                        remember(formattedTime, maxWidthPx) {
                                calculateTimeFontSize(
                                        timeText = formattedTime,
                                        textMeasurer = textMeasurer,
                                        maxWidthPx = maxWidthPx,
                                        density = density
                                )
                        }

                // Calculate date font size: max size that fits full width as single line
                val dateFontSize =
                        remember(formattedDate, maxWidthPx) {
                                calculateDateFontSize(
                                        dateText = formattedDate,
                                        textMeasurer = textMeasurer,
                                        maxWidthPx = maxWidthPx,
                                        density = density
                                )
                        }

                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        // Time
                        Text(
                                text = formattedTime,
                                style =
                                        TextStyle(
                                                color = colorScheme.timeColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = timeFontSize,
                                                lineHeight = timeFontSize * 1.1f
                                        ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(DisplayConstants.CLOCK_VERTICAL_SPACING))

                        // Date
                        Text(
                                text = formattedDate,
                                style =
                                        TextStyle(
                                                color = colorScheme.dateColor,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = dateFontSize,
                                                lineHeight = dateFontSize * 1.15f
                                        ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                        )
                }
        }
}

/** Calculate time font size: MAX_FONT_SIZE or smaller if needed to fit width as single line. */
private fun calculateTimeFontSize(
        timeText: String,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        maxWidthPx: Int,
        density: androidx.compose.ui.unit.Density
): TextUnit {
        val maxFontSizeSp = with(density) { DisplayConstants.MAX_FONT_SIZE.toPx() / fontScale }
        val minFontSizeSp = with(density) { DisplayConstants.MIN_FONT_SIZE.toPx() / fontScale }

        val fontSizeSp =
                findMaxFontSizeThatFitsWidth(
                        text = timeText,
                        textMeasurer = textMeasurer,
                        maxWidthPx = maxWidthPx,
                        minFontSizeSp = minFontSizeSp,
                        maxFontSizeSp = maxFontSizeSp
                )

        return fontSizeSp.sp
}

/**
 * Calculate date font size: max size that fits full width as a single line (independent of time).
 */
private fun calculateDateFontSize(
        dateText: String,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        maxWidthPx: Int,
        density: androidx.compose.ui.unit.Density
): TextUnit {
        val maxFontSizeSp = with(density) { DisplayConstants.MAX_FONT_SIZE.toPx() / fontScale }
        val minFontSizeSp = with(density) { DisplayConstants.MIN_FONT_SIZE.toPx() / fontScale }

        val fontSizeSp =
                findMaxFontSizeThatFitsWidth(
                        text = dateText,
                        textMeasurer = textMeasurer,
                        maxWidthPx = maxWidthPx,
                        minFontSizeSp = minFontSizeSp,
                        maxFontSizeSp = maxFontSizeSp
                )

        return fontSizeSp.sp
}

/** Binary search to find the maximum font size that fits within width as single line. */
private fun findMaxFontSizeThatFitsWidth(
        text: String,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        maxWidthPx: Int,
        minFontSizeSp: Float,
        maxFontSizeSp: Float
): Float {
        if (text.isEmpty()) return maxFontSizeSp
        if (maxWidthPx <= 0) return minFontSizeSp

        var low = minFontSizeSp
        var high = maxFontSizeSp
        var result = minFontSizeSp

        while (high - low > 0.5f) {
                val mid = (low + high) / 2f

                val testStyle = TextStyle(fontSize = mid.sp, lineHeight = (mid * 1.15f).sp)

                // Measure with infinite width to get single-line width
                val measureResult =
                        textMeasurer.measure(
                                text = text,
                                style = testStyle,
                                constraints = Constraints(maxWidth = Int.MAX_VALUE)
                        )

                if (measureResult.size.width <= maxWidthPx) {
                        result = mid
                        low = mid
                } else {
                        high = mid
                }
        }

        return result
}

// region Previews

@Preview(
        name = "Clock Display - Landscape Awake",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun ClockDisplayLandscapeAwakePreview() {
        MemoryLinkTheme {
                ClockDisplay(
                        time = LocalTime.of(14, 30),
                        date = LocalDate.of(2026, 2, 11),
                        use24HourFormat = false,
                        colorScheme = ClockColorScheme.Awake
                )
        }
}

@Preview(
        name = "Clock Display - Landscape Sleep",
        showBackground = true,
        backgroundColor = 0xFF0A0A0A,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun ClockDisplayLandscapeSleepPreview() {
        MemoryLinkTheme {
                ClockDisplay(
                        time = LocalTime.of(23, 45),
                        date = LocalDate.of(2026, 2, 11),
                        use24HourFormat = false,
                        colorScheme = ClockColorScheme.Sleep
                )
        }
}

@Preview(
        name = "Clock Display - Portrait Awake",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 480,
        heightDp = 800
)
@Composable
private fun ClockDisplayPortraitAwakePreview() {
        MemoryLinkTheme {
                ClockDisplay(
                        time = LocalTime.of(9, 5),
                        date = LocalDate.of(2026, 12, 25),
                        use24HourFormat = false,
                        colorScheme = ClockColorScheme.Awake
                )
        }
}

@Preview(
        name = "Clock Display - Portrait Sleep",
        showBackground = true,
        backgroundColor = 0xFF0A0A0A,
        widthDp = 480,
        heightDp = 800
)
@Composable
private fun ClockDisplayPortraitSleepPreview() {
        MemoryLinkTheme {
                ClockDisplay(
                        time = LocalTime.of(3, 30),
                        date = LocalDate.of(2026, 2, 11),
                        use24HourFormat = false,
                        colorScheme = ClockColorScheme.Sleep
                )
        }
}

@Preview(
        name = "Clock Display - 24 Hour Format",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun ClockDisplay24HourPreview() {
        MemoryLinkTheme {
                ClockDisplay(
                        time = LocalTime.of(14, 30),
                        date = LocalDate.of(2026, 2, 11),
                        use24HourFormat = true,
                        colorScheme = ClockColorScheme.Awake
                )
        }
}

@Preview(
        name = "Clock Display - Tablet Large",
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
                        colorScheme = ClockColorScheme.Awake
                )
        }
}

// endregion
