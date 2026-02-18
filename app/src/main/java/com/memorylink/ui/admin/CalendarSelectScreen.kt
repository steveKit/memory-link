package com.memorylink.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memorylink.ui.theme.AccentBlue
import com.memorylink.ui.theme.DarkBackground
import com.memorylink.ui.theme.MemoryLinkTheme

/**
 * Calendar selection screen.
 *
 * Lists available calendars from Google and allows selection for:
 * - Primary Calendar: The main calendar for events
 * - Holiday Calendar (optional): Secondary calendar for holidays
 *
 * @param calendarState Current calendar list state
 * @param onCalendarSelected Called when the main calendar is selected (id, name)
 * @param onHolidayCalendarSelected Called when a holiday calendar is selected (id, name)
 * @param onHolidayCalendarCleared Called when holiday calendar is cleared
 * @param onRefresh Refresh the calendar list
 * @param onBackClick Navigate back to admin home
 * @param modifier Modifier for the screen
 */
@Composable
fun CalendarSelectScreen(
        calendarState: CalendarState,
        onCalendarSelected: (String, String) -> Unit,
        onHolidayCalendarSelected: (String, String) -> Unit,
        onHolidayCalendarCleared: () -> Unit,
        onRefresh: () -> Unit,
        onBackClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(DarkBackground).padding(24.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with back button
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBackClick) {
                    Text(text = "← Back", fontSize = 16.sp, color = AccentBlue)
                }

                Spacer(modifier = Modifier.weight(1f))

                if (!calendarState.isLoading) {
                    TextButton(onClick = onRefresh) {
                        Text(text = "Refresh", fontSize = 16.sp, color = AccentBlue)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                    text = "Select Calendar",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                    text = "Choose which calendar to display events from",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            when {
                calendarState.isLoading -> {
                    // Loading state
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                    color = AccentBlue,
                                    modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                    text = "Loading calendars...",
                                    fontSize = 16.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                calendarState.error != null -> {
                    // Error state
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "⚠️", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                    text = calendarState.error,
                                    fontSize = 16.sp,
                                    color = Color(0xFFEF5350),
                                    textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            TextButton(onClick = onRefresh) {
                                Text(text = "Try Again", fontSize = 16.sp, color = AccentBlue)
                            }
                        }
                    }
                }
                calendarState.calendars.isEmpty() -> {
                    // Empty state
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "📅", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                    text = "No calendars found",
                                    fontSize = 16.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                else -> {
                    // Calendar list with sections
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // Primary Calendar Section
                        item {
                            Text(
                                    text = "📅 Primary Calendar",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                    text = "Main calendar for events and reminders",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        items(calendarState.calendars) { calendar ->
                            CalendarListItem(
                                    calendar = calendar,
                                    isSelected = calendar.id == calendarState.selectedCalendarId,
                                    onClick = { onCalendarSelected(calendar.id, calendar.name) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Holiday Calendar Section
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                    text = "🎄 Holiday Calendar (Optional)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                    text = "Shows holidays on the display. Can be toggled in Settings.",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // "None" option for holiday calendar
                        item {
                            HolidayCalendarListItem(
                                    name = "None",
                                    isSelected = calendarState.holidayCalendarId == null,
                                    onClick = { onHolidayCalendarCleared() }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Calendar options for holiday (exclude the selected primary calendar)
                        items(
                                calendarState.calendars.filter {
                                    it.id != calendarState.selectedCalendarId
                                }
                        ) { calendar ->
                            HolidayCalendarListItem(
                                    name = calendar.name,
                                    isSelected = calendar.id == calendarState.holidayCalendarId,
                                    onClick = {
                                        onHolidayCalendarSelected(calendar.id, calendar.name)
                                    }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Bottom padding
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarListItem(
        calendar: CalendarItem,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    Row(
            modifier =
                    modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                    if (isSelected) AccentBlue.copy(alpha = 0.2f)
                                    else Color(0xFF1E1E1E)
                            )
                            .clickable(onClick = onClick)
                            .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection indicator
        Box(
                modifier =
                        Modifier.size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) AccentBlue else Color(0xFF3A3A3A)),
                contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Text(text = "✓", fontSize = 14.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = calendar.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
            )
            if (calendar.isPrimary) {
                Text(text = "Primary calendar", fontSize = 12.sp, color = AccentBlue)
            }
        }
    }
}

/**
 * List item for holiday calendar selection.
 * Uses a slightly different style to distinguish from primary calendars.
 */
@Composable
private fun HolidayCalendarListItem(
        name: String,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    val isNone = name == "None"
    Row(
            modifier =
                    modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                    if (isSelected) Color(0xFF2E7D32).copy(alpha = 0.2f)
                                    else Color(0xFF1E1E1E)
                            )
                            .clickable(onClick = onClick)
                            .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection indicator (green for holidays)
        Box(
                modifier =
                        Modifier.size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFF4CAF50) else Color(0xFF3A3A3A)),
                contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Text(text = "✓", fontSize = 14.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isNone) Color.White.copy(alpha = 0.6f) else Color.White
            )
            if (!isNone) {
                Text(text = "Syncs weekly", fontSize = 12.sp, color = Color(0xFF4CAF50))
            }
        }
    }
}

// region Previews

@Preview(
        name = "Calendar Select - With Calendars",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 800
)
@Composable
private fun CalendarSelectWithCalendarsPreview() {
    MemoryLinkTheme {
        CalendarSelectScreen(
                calendarState =
                        CalendarState(
                                calendars =
                                        listOf(
                                                CalendarItem("1", "Grandma's Calendar", false),
                                                CalendarItem("2", "family@example.com", true),
                                                CalendarItem("3", "Birthdays", false),
                                                CalendarItem("4", "Holidays in Canada", false)
                                        ),
                                selectedCalendarId = "1"
                        ),
                onCalendarSelected = { _, _ -> },
                onHolidayCalendarSelected = { _, _ -> },
                onHolidayCalendarCleared = {},
                onRefresh = {},
                onBackClick = {}
        )
    }
}

@Preview(
        name = "Calendar Select - With Holiday Selected",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 800
)
@Composable
private fun CalendarSelectWithHolidayPreview() {
    MemoryLinkTheme {
        CalendarSelectScreen(
                calendarState =
                        CalendarState(
                                calendars =
                                        listOf(
                                                CalendarItem("1", "Grandma's Calendar", false),
                                                CalendarItem("2", "family@example.com", true),
                                                CalendarItem("3", "Birthdays", false),
                                                CalendarItem("4", "Holidays in Canada", false)
                                        ),
                                selectedCalendarId = "1",
                                holidayCalendarId = "4",
                                holidayCalendarName = "Holidays in Canada"
                        ),
                onCalendarSelected = { _, _ -> },
                onHolidayCalendarSelected = { _, _ -> },
                onHolidayCalendarCleared = {},
                onRefresh = {},
                onBackClick = {}
        )
    }
}

@Preview(
        name = "Calendar Select - Loading",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 600
)
@Composable
private fun CalendarSelectLoadingPreview() {
    MemoryLinkTheme {
        CalendarSelectScreen(
                calendarState = CalendarState(isLoading = true),
                onCalendarSelected = { _, _ -> },
                onHolidayCalendarSelected = { _, _ -> },
                onHolidayCalendarCleared = {},
                onRefresh = {},
                onBackClick = {}
        )
    }
}

@Preview(
        name = "Calendar Select - Error",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 600
)
@Composable
private fun CalendarSelectErrorPreview() {
    MemoryLinkTheme {
        CalendarSelectScreen(
                calendarState =
                        CalendarState(
                                error = "Failed to load calendars. Please check your connection."
                        ),
                onCalendarSelected = { _, _ -> },
                onHolidayCalendarSelected = { _, _ -> },
                onHolidayCalendarCleared = {},
                onRefresh = {},
                onBackClick = {}
        )
    }
}

@Preview(
        name = "Calendar Select - Empty",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 600
)
@Composable
private fun CalendarSelectEmptyPreview() {
    MemoryLinkTheme {
        CalendarSelectScreen(
                calendarState = CalendarState(calendars = emptyList()),
                onCalendarSelected = { _, _ -> },
                onHolidayCalendarSelected = { _, _ -> },
                onHolidayCalendarCleared = {},
                onRefresh = {},
                onBackClick = {}
        )
    }
}

// endregion
