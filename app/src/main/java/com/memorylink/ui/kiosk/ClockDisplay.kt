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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.memorylink.domain.model.AllDayEventInfo
import com.memorylink.ui.theme.AccentBlue
import com.memorylink.ui.theme.DisplayConstants
import com.memorylink.ui.theme.MemoryLinkTheme
import com.memorylink.ui.theme.SleepText
import com.memorylink.ui.theme.TextDate
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
 * - Single-day events:
 * ```
 *     - Today: "Today is {title}"
 *     - Tomorrow: "Tomorrow is {title}"
 *     - Future: "{Day of week} is {title}"
 * ```
 * - Multi-day events (ongoing, started before today):
 * ```
 *     - "{title} until {end day/date}"
 * ```
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
 * @param allDayEventDate Start date of all-day event, or null if today
 * @param allDayEventEndDate End date for multi-day events (inclusive), or null if single-day
 * @param colorScheme Colors for time, date, and all-day event text
 * @param modifier Modifier for the root container
 */
@Composable
fun ClockDisplay(
        time: LocalTime,
        date: LocalDate,
        use24HourFormat: Boolean,
        showYearInDate: Boolean,
        allDayEventTitle: String? = null,
        allDayEventDate: LocalDate? = null,
        allDayEventEndDate: LocalDate? = null,
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
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val formattedAllDayEvent =
                if (allDayEventTitle != null) {
                        // Check if this is an ongoing multi-day event (started before today)
                        // Indicated by: allDayEventEndDate is set AND allDayEventDate is null
                        // (null date means "today or earlier" in this context)
                        val isOngoingMultiDay =
                                allDayEventEndDate != null && allDayEventDate == null

                        when {
                                isOngoingMultiDay -> {
                                        // Ongoing multi-day event: "{title} until {end day/date}"
                                        val endDateText =
                                                formatEndDate(allDayEventEndDate!!, today, tomorrow)
                                        "$allDayEventTitle until $endDateText"
                                }
                                allDayEventDate == null -> {
                                        // Single-day event today: "Today is {title}"
                                        "Today is $allDayEventTitle"
                                }
                                allDayEventDate == tomorrow -> {
                                        // Tomorrow: "Tomorrow is {title}"
                                        "Tomorrow is $allDayEventTitle"
                                }
                                else -> {
                                        // Future day (including future multi-day events):
                                        // "{Day of week} is {title}"
                                        val dayName =
                                                allDayEventDate.dayOfWeek.getDisplayName(
                                                        JavaTextStyle.FULL,
                                                        Locale.getDefault()
                                                )
                                        "$dayName is $allDayEventTitle"
                                }
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
                                maxLines = 1,
                                softWrap = false,
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

/**
 * Format the end date for multi-day events.
 *
 * Uses friendly terms when appropriate:
 * - "today" if end date is today
 * - "tomorrow" if end date is tomorrow
 * - Day name (e.g., "Wednesday") if within 7 days
 * - Full date (e.g., "February 21") if further out
 *
 * @param endDate The end date to format (inclusive)
 * @param today Today's date
 * @param tomorrow Tomorrow's date
 * @return Formatted end date string
 */
private fun formatEndDate(endDate: LocalDate, today: LocalDate, tomorrow: LocalDate): String {
        return when {
                endDate == today -> "today"
                endDate == tomorrow -> "tomorrow"
                endDate.isBefore(today.plusDays(7)) -> {
                        // Within a week - use day name
                        endDate.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
                }
                else -> {
                        // Further out - use "Month day" format
                        val formatter = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())
                        endDate.format(formatter)
                }
        }
}

/**
 * Displays the current time, date, and multiple all-day events.
 *
 * This overload accepts a list of AllDayEventInfo objects for displaying multiple events, each on a
 * separate line below the date.
 *
 * @param time The current time to display
 * @param date The current date to display
 * @param use24HourFormat Whether to use 24-hour format
 * @param showYearInDate Whether to show year in date
 * @param allDayEvents List of all-day events to display (each on separate line)
 * @param colorScheme Colors for time, date, and all-day event text
 * @param modifier Modifier for the root container
 */
@Composable
fun ClockDisplay(
        time: LocalTime,
        date: LocalDate,
        use24HourFormat: Boolean,
        showYearInDate: Boolean,
        allDayEvents: List<AllDayEventInfo>,
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

        val formattedTime =
                time.format(timeFormatter)
                        .replace("AM", "am")
                        .replace("PM", "pm")
                        .replace("a.m.", "am")
                        .replace("p.m.", "pm")

        val datePattern = if (showYearInDate) "EEEE, MMMM d, yyyy" else "EEEE, MMMM d"
        val dateFormatter = DateTimeFormatter.ofPattern(datePattern, Locale.getDefault())
        val formattedDate = date.format(dateFormatter)

        // Format all all-day events
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val formattedEvents =
                allDayEvents.map { event -> formatAllDayEvent(event, today, tomorrow) }

        // Find the longest event text for font sizing
        val longestEventText = formattedEvents.maxByOrNull { it.length }

        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current

                val maxWidthPx = constraints.maxWidth
                val maxHeightPx = constraints.maxHeight

                val (timeFontSize, dateFontSize, allDayFontSize) =
                        remember(
                                formattedTime,
                                formattedDate,
                                longestEventText,
                                formattedEvents.size,
                                maxWidthPx,
                                maxHeightPx,
                                isLandscape
                        ) {
                                calculateOptimalFontSizesMultiple(
                                        timeText = formattedTime,
                                        dateText = formattedDate,
                                        allDayEventTexts = formattedEvents,
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
                                maxLines = 1,
                                softWrap = false,
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

                        // All-day events (each on separate line, consistent font size)
                        // All events use the same font size (smallest needed to fit longest event)
                        // to maintain visual consistency across multiple events
                        formattedEvents.forEach { eventText ->
                                Spacer(
                                        modifier =
                                                Modifier.height(
                                                        DisplayConstants.CLOCK_VERTICAL_SPACING / 2
                                                )
                                )

                                Text(
                                        text = eventText,
                                        style =
                                                TextStyle(
                                                        color = colorScheme.allDayEventColor,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = allDayFontSize,
                                                        lineHeight = allDayFontSize * 1.15f
                                                ),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                )
                        }
                }
        }
}

/**
 * Format a single all-day event for display.
 *
 * @param event The event info
 * @param today Today's date
 * @param tomorrow Tomorrow's date
 * @return Formatted event string
 */
private fun formatAllDayEvent(
        event: AllDayEventInfo,
        today: LocalDate,
        tomorrow: LocalDate
): String {
        // Check if this is an ongoing multi-day event
        val isOngoingMultiDay = event.endDate != null && event.startDate == null

        return when {
                isOngoingMultiDay -> {
                        // Ongoing multi-day event: "{title} until {end day/date}"
                        val endDateText = formatEndDate(event.endDate!!, today, tomorrow)
                        "${event.title} until $endDateText"
                }
                event.startDate == null -> {
                        // Single-day event today: "Today is {title}"
                        "Today is ${event.title}"
                }
                event.startDate == tomorrow -> {
                        // Tomorrow: "Tomorrow is {title}"
                        "Tomorrow is ${event.title}"
                }
                else -> {
                        // Future day: "{Day of week} is {title}"
                        val dayName =
                                event.startDate.dayOfWeek.getDisplayName(
                                        JavaTextStyle.FULL,
                                        Locale.getDefault()
                                )
                        "$dayName is ${event.title}"
                }
        }
}

/**
 * Calculate optimal font sizes for time, date, and multiple all-day events.
 *
 * Uses the longest event text to determine font size, then applies same size to all events.
 */
private fun calculateOptimalFontSizesMultiple(
        timeText: String,
        dateText: String,
        allDayEventTexts: List<String>,
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

        // Time: sized independently
        val timeFontSizeSp =
                findMaxFontSizeThatFits(
                        text = timeText,
                        textMeasurer = textMeasurer,
                        maxWidthPx = maxWidthPx,
                        maxHeightPx = Int.MAX_VALUE,
                        minFontSizeSp = minFontSizeSp,
                        maxFontSizeSp = maxTimeFontSizeSp,
                        softWrap = false
                )

        // Date: sized independently
        val dateFontSizeSp =
                findMaxFontSizeThatFits(
                        text = dateText,
                        textMeasurer = textMeasurer,
                        maxWidthPx = maxWidthPx,
                        maxHeightPx = Int.MAX_VALUE,
                        minFontSizeSp = minFontSizeSp,
                        maxFontSizeSp = maxDateFontSizeSp,
                        softWrap = false
                )

        // All-day events: find size that fits the longest text
        val allDayFontSizeSp =
                if (allDayEventTexts.isNotEmpty()) {
                        val longestText = allDayEventTexts.maxByOrNull { it.length } ?: ""
                        findMaxFontSizeThatFits(
                                text = longestText,
                                textMeasurer = textMeasurer,
                                maxWidthPx = maxWidthPx,
                                maxHeightPx = Int.MAX_VALUE,
                                minFontSizeSp = minFontSizeSp,
                                maxFontSizeSp = maxAllDayFontSizeSp,
                                softWrap = false
                        )
                } else {
                        maxAllDayFontSizeSp
                }

        // Verify all fit together in available height
        val totalHeight =
                estimateTotalHeightMultiple(
                        timeFontSizeSp,
                        dateFontSizeSp,
                        allDayFontSizeSp,
                        allDayEventTexts.size,
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

/** Estimate total height of time + date + multiple all-day events with spacing. */
private fun estimateTotalHeightMultiple(
        timeFontSizeSp: Float,
        dateFontSizeSp: Float,
        allDayFontSizeSp: Float,
        eventCount: Int,
        density: androidx.compose.ui.unit.Density
): Float {
        val timeHeight = timeFontSizeSp * 1.15f
        val dateHeight = dateFontSizeSp * 1.15f
        val spacing = with(density) { DisplayConstants.CLOCK_VERTICAL_SPACING.toPx() }
        val halfSpacing = spacing / 2

        var total = timeHeight + dateHeight + spacing

        if (eventCount > 0) {
                val allDayHeight = allDayFontSizeSp * 1.15f
                // Each event adds its height plus half spacing
                total += (allDayHeight + halfSpacing) * eventCount
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
                        showYearInDate = false,
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
                        showYearInDate = false,
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
                        showYearInDate = false,
                        allDayEventTitle = "Mom's Birthday",
                        allDayEventDate = null, // null = today
                        colorScheme = ClockColorScheme.Awake
                )
        }
}

@Preview(
        name = "Clock Display - With All-Day Event Tomorrow",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 480
)
@Composable
private fun ClockDisplayAllDayTomorrowPreview() {
        MemoryLinkTheme {
                ClockDisplay(
                        time = LocalTime.of(10, 30),
                        date = LocalDate.of(2026, 2, 11),
                        use24HourFormat = false,
                        showYearInDate = false,
                        allDayEventTitle = "Family Reunion",
                        allDayEventDate = LocalDate.now().plusDays(1), // tomorrow
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
                        showYearInDate = false,
                        allDayEventTitle = "Company Retreat",
                        allDayEventDate = LocalDate.of(2026, 2, 13), // Friday (future)
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
                        showYearInDate = false,
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
                        showYearInDate = false,
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
                        showYearInDate = false,
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
                        showYearInDate = false,
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
                        showYearInDate = false,
                        allDayEventTitle = "Annual Family Reunion at Grandma's House",
                        allDayEventDate = LocalDate.of(2026, 2, 14), // Saturday (future)
                        colorScheme = ClockColorScheme.Awake
                )
        }
}

// endregion
