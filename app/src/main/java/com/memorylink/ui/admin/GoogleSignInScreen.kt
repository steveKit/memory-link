package com.memorylink.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
 * Google Sign-In screen for account management.
 *
 * Shows:
 * - Sign-in button when not signed in
 * - Account info and sign-out when signed in
 *
 * @param authState Current authentication state
 * @param onSignInClick Trigger Google Sign-In flow
 * @param onSignOutClick Sign out from Google
 * @param onBackClick Navigate back to admin home
 * @param modifier Modifier for the screen
 */
@Composable
fun GoogleSignInScreen(
        authState: AuthState,
        onSignInClick: () -> Unit,
        onSignOutClick: () -> Unit,
        onBackClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(DarkBackground).padding(24.dp)) {
        Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with back button
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBackClick) {
                    Text(text = "← Back", fontSize = 16.sp, color = AccentBlue)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                    text = "Google Account",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (authState.isLoading) {
                // Loading state
                CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                        text = "Signing in...",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                )
            } else if (authState.isSignedIn) {
                // Signed in state
                SignedInContent(
                        email = authState.userEmail ?: "Unknown",
                        onSignOutClick = onSignOutClick
                )
            } else {
                // Not signed in state
                SignInContent(error = authState.error, onSignInClick = onSignInClick)
            }
        }
    }
}

@Composable
private fun SignedInContent(email: String, onSignOutClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Account avatar placeholder
        Box(
                modifier = Modifier.size(80.dp).clip(CircleShape).background(AccentBlue),
                contentAlignment = Alignment.Center
        ) {
            Text(
                    text = email.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Signed in as", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))

        Spacer(modifier = Modifier.height(4.dp))

        Text(text = email, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White)

        Spacer(modifier = Modifier.height(48.dp))

        // Sign out button
        Button(
                onClick = onSignOutClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                    text = "Sign Out",
                    fontSize = 16.sp,
                    color = Color(0xFFEF5350) // Red for destructive action
            )
        }
    }
}

@Composable
private fun SignInContent(error: String?, onSignInClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Google logo placeholder
        Box(
                modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
        ) {
            Text(
                    text = "G",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4285F4) // Google blue
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
                text = "Sign in with Google to sync\nyour calendar events",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                    text = error,
                    fontSize = 14.sp,
                    color = Color(0xFFEF5350),
                    textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Sign in button
        Button(
                onClick = onSignInClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                    text = "Sign in with Google",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1F1F1F)
            )
        }
    }
}

// region Previews

@Preview(
        name = "Google Sign-In - Not Signed In",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 600
)
@Composable
private fun GoogleSignInNotSignedInPreview() {
    MemoryLinkTheme {
        GoogleSignInScreen(
                authState = AuthState(isSignedIn = false),
                onSignInClick = {},
                onSignOutClick = {},
                onBackClick = {}
        )
    }
}

@Preview(
        name = "Google Sign-In - Signed In",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 600
)
@Composable
private fun GoogleSignInSignedInPreview() {
    MemoryLinkTheme {
        GoogleSignInScreen(
                authState = AuthState(isSignedIn = true, userEmail = "family@example.com"),
                onSignInClick = {},
                onSignOutClick = {},
                onBackClick = {}
        )
    }
}

@Preview(
        name = "Google Sign-In - Loading",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 600
)
@Composable
private fun GoogleSignInLoadingPreview() {
    MemoryLinkTheme {
        GoogleSignInScreen(
                authState = AuthState(isLoading = true),
                onSignInClick = {},
                onSignOutClick = {},
                onBackClick = {}
        )
    }
}

@Preview(
        name = "Google Sign-In - Error",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 600
)
@Composable
private fun GoogleSignInErrorPreview() {
    MemoryLinkTheme {
        GoogleSignInScreen(
                authState =
                        AuthState(
                                isSignedIn = false,
                                error = "Sign-in failed. Please check your internet connection."
                        ),
                onSignInClick = {},
                onSignOutClick = {},
                onBackClick = {}
        )
    }
}

// endregion
