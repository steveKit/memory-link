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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memorylink.ui.theme.AccentBlue
import com.memorylink.ui.theme.DarkBackground
import com.memorylink.ui.theme.MemoryLinkTheme

/**
 * Admin home screen showing main menu options.
 *
 * @param authState Current authentication state
 * @param selectedCalendarName Name of the currently selected calendar (null if none)
 * @param syncState Current sync state (loading, result)
 * @param lastSyncFormatted Human-readable last sync time
 * @param onGoogleAccountClick Navigate to Google account screen
 * @param onCalendarClick Navigate to calendar selection screen
 * @param onSettingsClick Navigate to manual config screen
 * @param onSyncNowClick Trigger manual calendar sync
 * @param onExitAdmin Exit admin mode and return to kiosk
 * @param onExitApp Exit the app entirely
 * @param modifier Modifier for the screen
 */
@Composable
fun AdminHomeScreen(
        authState: AuthState,
        selectedCalendarName: String?,
        syncState: SyncState,
        lastSyncFormatted: String,
        onGoogleAccountClick: () -> Unit,
        onCalendarClick: () -> Unit,
        onSettingsClick: () -> Unit,
        onSyncNowClick: () -> Unit,
        onExitAdmin: () -> Unit,
        onExitApp: () -> Unit,
        modifier: Modifier = Modifier
) {
        Box(modifier = modifier.fillMaxSize().background(DarkBackground).padding(24.dp)) {
                Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        // Header
                        Text(
                                text = "Admin Settings",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                                text = "Configure your MemoryLink display",
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Menu items
                        AdminMenuItem(
                                title = "Google Account",
                                subtitle =
                                        if (authState.isSignedIn) {
                                                authState.userEmail ?: "Signed in"
                                        } else {
                                                "Not signed in"
                                        },
                                icon = "👤",
                                onClick = onGoogleAccountClick
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AdminMenuItem(
                                title = "Calendar",
                                subtitle = selectedCalendarName ?: "Not selected",
                                icon = "📅",
                                onClick = onCalendarClick,
                                enabled = authState.isSignedIn
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AdminMenuItem(
                                title = "Display Settings",
                                subtitle = "Wake/sleep times, brightness, format",
                                icon = "⚙️",
                                onClick = onSettingsClick
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sync Now button
                        AdminMenuItem(
                                title = "Sync Calendar",
                                subtitle =
                                        if (syncState.isSyncing) {
                                                "Syncing..."
                                        } else {
                                                syncState.lastResult
                                                        ?: "Last sync: $lastSyncFormatted"
                                        },
                                icon = "🔄",
                                onClick = onSyncNowClick,
                                enabled =
                                        authState.isSignedIn &&
                                                selectedCalendarName != null &&
                                                !syncState.isSyncing
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Exit Admin button
                        Button(
                                onClick = onExitAdmin,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF2A2A2A)
                                        ),
                                shape = RoundedCornerShape(12.dp)
                        ) { Text(text = "Exit Admin Mode", fontSize = 18.sp, color = AccentBlue) }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Exit App button
                        Button(
                                onClick = onExitApp,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF3A1515)
                                        ),
                                shape = RoundedCornerShape(12.dp)
                        ) { Text(text = "Exit App", fontSize = 18.sp, color = Color(0xFFEF5350)) }
                }
        }
}

@Composable
private fun AdminMenuItem(
        title: String,
        subtitle: String,
        icon: String,
        onClick: () -> Unit,
        enabled: Boolean = true,
        modifier: Modifier = Modifier
) {
        Row(
                modifier =
                        modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (enabled) Color(0xFF1E1E1E) else Color(0xFF151515))
                                .clickable(enabled = enabled, onClick = onClick)
                                .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                // Icon
                Text(text = icon, fontSize = 32.sp)

                Spacer(modifier = Modifier.width(16.dp))

                // Text content
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                                text = subtitle,
                                fontSize = 14.sp,
                                color =
                                        if (enabled) {
                                                Color.White.copy(alpha = 0.7f)
                                        } else {
                                                Color.White.copy(alpha = 0.3f)
                                        }
                        )
                }

                // Arrow
                Text(
                        text = "›",
                        fontSize = 28.sp,
                        color = if (enabled) AccentBlue else AccentBlue.copy(alpha = 0.3f)
                )
        }
}

// region Previews

@Preview(
        name = "Admin Home - Signed In",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 700
)
@Composable
private fun AdminHomeSignedInPreview() {
        MemoryLinkTheme {
                AdminHomeScreen(
                        authState = AuthState(isSignedIn = true, userEmail = "family@example.com"),
                        selectedCalendarName = "Grandma's Calendar",
                        syncState = SyncState(lastResult = "Synced 3 events"),
                        lastSyncFormatted = "2 min ago",
                        onGoogleAccountClick = {},
                        onCalendarClick = {},
                        onSettingsClick = {},
                        onSyncNowClick = {},
                        onExitAdmin = {},
                        onExitApp = {}
                )
        }
}

@Preview(
        name = "Admin Home - Not Signed In",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 700
)
@Composable
private fun AdminHomeNotSignedInPreview() {
        MemoryLinkTheme {
                AdminHomeScreen(
                        authState = AuthState(isSignedIn = false),
                        selectedCalendarName = null,
                        syncState = SyncState(),
                        lastSyncFormatted = "Never",
                        onGoogleAccountClick = {},
                        onCalendarClick = {},
                        onSettingsClick = {},
                        onSyncNowClick = {},
                        onExitAdmin = {},
                        onExitApp = {}
                )
        }
}

// endregion
