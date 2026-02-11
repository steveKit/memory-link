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
 * Lists available calendars from Google and allows selection.
 *
 * @param calendarState Current calendar list state
 * @param onCalendarSelected Called when a calendar is selected
 * @param onRefresh Refresh the calendar list
 * @param onBackClick Navigate back to admin home
 * @param modifier Modifier for the screen
 */
@Composable
fun CalendarSelectScreen(
        calendarState: CalendarState,
        onCalendarSelected: (String) -> Unit,
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
                    // Calendar list
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(calendarState.calendars) { calendar ->
                            CalendarListItem(
                                    calendar = calendar,
                                    isSelected = calendar.id == calendarState.selectedCalendarId,
                                    onClick = { onCalendarSelected(calendar.id) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
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

// region Previews

@Preview(
        name = "Calendar Select - With Calendars",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 600
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
                onCalendarSelected = {},
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
                onCalendarSelected = {},
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
                onCalendarSelected = {},
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
                onCalendarSelected = {},
                onRefresh = {},
                onBackClick = {}
        )
    }
}

// endregion
