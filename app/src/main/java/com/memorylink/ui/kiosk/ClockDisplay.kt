package com.memorylink.ui.kiosk

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Color scheme for the clock display.
 *
 * Allows the same ClockDisplay composable to be used for both awake and sleep modes by passing
 * different color configurations.
 */
data class ClockColorScheme(
        val timeColor: Color,
        val dateColor: Color,
        val allDayEventColor: Color = AccentBlue
) {
        companion object {
                /** Bright colors for awake/daytime mode. */
                val Awake =
                        ClockColorScheme(
                                timeColor = AccentBlue,
                                dateColor = TextDate,
                                allDayEventColor = AccentBlue
                        )

                /** Dimmed colors for sleep mode. */
                val Sleep =
                        ClockColorScheme(
                                timeColor = SleepText,
                                dateColor = SleepText,
                                allDayEventColor = SleepText
                        )
        }
}

/**
 * Displays the current time, date, and optional all-day event, centered as a unit.
 *
 * Layout behavior:
 * - **Time:** Fills width as a single line (max: 150.sp)
 * - **Date:** Fills width as a single line (max: 95.sp)
 * - **All-day event:** Displayed below date in AccentBlue (max: 60.sp)
 * - Today: "Today is {title}"
 * - Future: "{Day of week} is {title}"
 *
 * Both are sized independently to maximize readability. If vertical space is limited, all elements
 * are scaled down proportionally to fit.
 *
 * This component is used for both AwakeNoEvent and Sleep display states.
 *
 * @param time The current time to display
 * @param date The current date to display
 * @param use24HourFormat Whether to use 24-hour format (default: false = 12-hour)
 * @param showYearInDate Whether to show year in date (default: true)
 * @param allDayEventTitle Optional all-day event title to display
 * @param allDayEventDayOfWeek Day of week for future all-day event, or null if today
 * @param colorScheme Colors for time, date, and all-day event text
 * @param modifier Modifier for the root container
 */
@Composable
fun ClockDisplay(
        time: LocalTime,
        date: LocalDate,
        use24HourFormat: Boolean = false,
        showYearInDate: Boolean = true,
        allDayEventTitle: String? = null,
        allDayEventDayOfWeek: DayOfWeek? = null,
        colorScheme: ClockColorScheme = ClockColorScheme.Awake,
        modifier: Modifier = Modifier
) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

        // Date format: with or without year based on showYearInDate setting
        // With year: "Wednesday, February 11, 2026"
        // Without year: "Wednesday, February 11"
        val datePattern = if (showYearInDate) "EEEE, MMMM d, yyyy" else "EEEE, MMMM d"
        val dateFormatter = DateTimeFormatter.ofPattern(datePattern, Locale.getDefault())
        val formattedDate = date.format(dateFormatter)

        // Format all-day event text if present
        val formattedAllDayEvent =
                if (allDayEventTitle != null) {
                        if (allDayEventDayOfWeek != null) {
                                // Future day: "{Day of week} is {title}"
                                val dayName =
                                        allDayEventDayOfWeek.getDisplayName(
                                                JavaTextStyle.FULL,
                                                Locale.getDefault()
                                        )
                                "$dayName is $allDayEventTitle"
                        } else {
                                // Today: "Today is {title}"
                                "Today is $allDayEventTitle"
                        }
                } else {
                        null
                }

        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current

                val maxWidthPx = constraints.maxWidth
                val maxHeightPx = constraints.maxHeight

                // Calculate optimal font sizes based on orientation and available space
                val (timeFontSize, dateFontSize, allDayFontSize) =
                        remember(
                                formattedTime,
                                formattedDate,
                                formattedAllDayEvent,
                                maxWidthPx,
                                maxHeightPx,
                                isLandscape
                        ) {
                                calculateOptimalFontSizes(
                                        timeText = formattedTime,
                                        dateText = formattedDate,
                                        allDayEventText = formattedAllDayEvent,
                                        textMeasurer = textMeasurer,
                                        maxWidthPx = maxWidthPx,
                                        maxHeightPx = maxHeightPx,
                                        isLandscape = isLandscape,
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

                        // All-day event (if present)
                        if (formattedAllDayEvent != null) {
                                Spacer(
                                        modifier =
                                                Modifier.height(
                                                        DisplayConstants.CLOCK_VERTICAL_SPACING
                                                )
                                )

                                Text(
                                        text = formattedAllDayEvent,
                                        style =
                                                TextStyle(
                                                        color = colorScheme.allDayEventColor,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = allDayFontSize,
                                                        lineHeight = allDayFontSize * 1.15f
                                                ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                )
                        }
                }
        }
}

/**
 * Result of font size calculations.
 *
 * @param timeFontSize Font size for time display
 * @param dateFontSize Font size for date display
 * @param allDayFontSize Font size for all-day event display
 */
private data class FontSizeResult(
        val timeFontSize: TextUnit,
        val dateFontSize: TextUnit,
        val allDayFontSize: TextUnit
)

/**
 * Calculate optimal font sizes for time, date, and optional all-day event.
 *
 * All elements are sized independently to fill width as a single line:
 * - Time: capped at [DisplayConstants.MAX_TIME_FONT_SIZE] (150.sp)
 * - Date: capped at [DisplayConstants.MAX_FONT_SIZE] (95.sp)
 * - All-day: capped at [DisplayConstants.MAX_ALL_DAY_FONT_SIZE] (60.sp)
 *
 * If the combined height exceeds available space, all are scaled down proportionally.
 */
private fun calculateOptimalFontSizes(
        timeText: String,
        dateText: String,
        allDayEventText: String?,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        maxWidthPx: Int,
        maxHeightPx: Int,
        @Suppress("UNUSED_PARAMETER") isLandscape: Boolean,
        density: androidx.compose.ui.unit.Density
): FontSizeResult {
        val maxTimeFontSizeSp =
                with(density) { DisplayConstants.MAX_TIME_FONT_SIZE.toPx() / fontScale }
        val maxDateFontSizeSp = with(density) { DisplayConstants.MAX_FONT_SIZE.toPx() / fontScale }
        val maxAllDayFontSizeSp =
                with(density) { DisplayConstants.MAX_ALL_DAY_FONT_SIZE.toPx() / fontScale }
        val minFontSizeSp = with(density) { DisplayConstants.MIN_FONT_SIZE.toPx() / fontScale }

        // Time: sized independently to fill width as single line
        val timeFontSizeSp =
                findMaxFontSizeThatFits(
                        text = timeText,
                        textMeasurer = textMeasurer,
                        maxWidthPx = maxWidthPx,
                        maxHeightPx = Int.MAX_VALUE, // No height constraint for initial sizing
                        minFontSizeSp = minFontSizeSp,
                        maxFontSizeSp = maxTimeFontSizeSp,
                        softWrap = false // Single line
                )

        // Date: sized independently to fill width as single line
        val dateFontSizeSp =
                findMaxFontSizeThatFits(
                        text = dateText,
                        textMeasurer = textMeasurer,
                        maxWidthPx = maxWidthPx,
                        maxHeightPx = Int.MAX_VALUE, // No height constraint for initial sizing
                        minFontSizeSp = minFontSizeSp,
                        maxFontSizeSp = maxDateFontSizeSp,
                        softWrap = false // Single line
                )

        // All-day event: sized independently (if present)
        val allDayFontSizeSp =
                if (allDayEventText != null) {
                        findMaxFontSizeThatFits(
                                text = allDayEventText,
                                textMeasurer = textMeasurer,
                                maxWidthPx = maxWidthPx,
                                maxHeightPx = Int.MAX_VALUE,
                                minFontSizeSp = minFontSizeSp,
                                maxFontSizeSp = maxAllDayFontSizeSp,
                                softWrap = false // Single line
                        )
                } else {
                        maxAllDayFontSizeSp
                }

        // Verify all fit together in available height
        val totalHeight =
                estimateTotalHeight(
                        timeFontSizeSp,
                        dateFontSizeSp,
                        if (allDayEventText != null) allDayFontSizeSp else null,
                        density
                )

        return if (totalHeight <= maxHeightPx) {
                FontSizeResult(timeFontSizeSp.sp, dateFontSizeSp.sp, allDayFontSizeSp.sp)
        } else {
                // Scale all down proportionally to fit height
                val scale = maxHeightPx.toFloat() / totalHeight
                val scaledTime = (timeFontSizeSp * scale).coerceAtLeast(minFontSizeSp)
                val scaledDate = (dateFontSizeSp * scale).coerceAtLeast(minFontSizeSp)
                val scaledAllDay = (allDayFontSizeSp * scale).coerceAtLeast(minFontSizeSp)
                FontSizeResult(scaledTime.sp, scaledDate.sp, scaledAllDay.sp)
        }
}

/** Binary search to find the maximum font size that fits within constraints. */
private fun findMaxFontSizeThatFits(
        text: String,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        maxWidthPx: Int,
        maxHeightPx: Int,
        minFontSizeSp: Float,
        maxFontSizeSp: Float,
        softWrap: Boolean
): Float {
        if (text.isEmpty()) return maxFontSizeSp
        if (maxWidthPx <= 0 || maxHeightPx <= 0) return minFontSizeSp

        var low = minFontSizeSp
        var high = maxFontSizeSp
        var result = minFontSizeSp

        while (high - low > 0.5f) {
                val mid = (low + high) / 2f

                val testStyle = TextStyle(fontSize = mid.sp, lineHeight = (mid * 1.15f).sp)

                val measureResult =
                        textMeasurer.measure(
                                text = text,
                                style = testStyle,
                                constraints =
                                        if (softWrap) {
                                                Constraints(
                                                        maxWidth = maxWidthPx,
                                                        maxHeight = Int.MAX_VALUE
                                                )
                                        } else {
                                                Constraints(
                                                        maxWidth = Int.MAX_VALUE,
                                                        maxHeight = Int.MAX_VALUE
                                                )
                                        }
                        )

                val fitsWidth = measureResult.size.width <= maxWidthPx
                val fitsHeight = measureResult.size.height <= maxHeightPx

                if (fitsWidth && fitsHeight) {
                        result = mid
                        low = mid
                } else {
                        high = mid
                }
        }

        return result
}

/** Estimate total height of time + date + optional all-day event with spacing. */
private fun estimateTotalHeight(
        timeFontSizeSp: Float,
        dateFontSizeSp: Float,
        allDayFontSizeSp: Float?,
        density: androidx.compose.ui.unit.Density
): Float {
        val timeHeight = timeFontSizeSp * 1.15f // lineHeight multiplier
        val dateHeight = dateFontSizeSp * 1.15f
        val spacing = with(density) { DisplayConstants.CLOCK_VERTICAL_SPACING.toPx() }

        var total = timeHeight + dateHeight + spacing

        if (allDayFontSizeSp != null) {
                val allDayHeight = allDayFontSizeSp * 1.15f
                total += allDayHeight + spacing
        }

        return total
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
        name = "Clock Display - With All-Day Event Today",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun ClockDisplayAllDayTodayPreview() {
        MemoryLinkTheme {
                ClockDisplay(
                        time = LocalTime.of(10, 30),
                        date = LocalDate.of(2026, 2, 11),
                        use24HourFormat = false,
                        allDayEventTitle = "Mom's Birthday",
                        allDayEventDayOfWeek = null, // null = today
                        colorScheme = ClockColorScheme.Awake
                )
        }
}

@Preview(
        name = "Clock Display - With All-Day Event Friday",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun ClockDisplayAllDayFuturePreview() {
        MemoryLinkTheme {
                ClockDisplay(
                        time = LocalTime.of(10, 30),
                        date = LocalDate.of(2026, 2, 11),
                        use24HourFormat = false,
                        allDayEventTitle = "Family Reunion",
                        allDayEventDayOfWeek = DayOfWeek.FRIDAY,
                        colorScheme = ClockColorScheme.Awake
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

@Preview(
        name = "Clock Display - With Long All-Day Event",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun ClockDisplayLongAllDayPreview() {
        MemoryLinkTheme {
                ClockDisplay(
                        time = LocalTime.of(10, 30),
                        date = LocalDate.of(2026, 2, 11),
                        use24HourFormat = false,
                        allDayEventTitle = "Annual Family Reunion at Grandma's House",
                        allDayEventDayOfWeek = DayOfWeek.SATURDAY,
                        colorScheme = ClockColorScheme.Awake
                )
        }
}

// endregion
