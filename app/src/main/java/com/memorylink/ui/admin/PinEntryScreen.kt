package com.memorylink.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memorylink.ui.components.NumericKeypad
import com.memorylink.ui.components.PinDotIndicator
import com.memorylink.ui.theme.AccentBlue
import com.memorylink.ui.theme.DarkBackground
import com.memorylink.ui.theme.MemoryLinkTheme

/**
 * PIN entry screen for admin mode access.
 *
 * Supports two modes:
 * - Setup mode: First-time PIN creation with confirmation
 * - Entry mode: PIN validation with lockout after 3 failed attempts
 *
 * Per .clinerules/20-android.md:
 * - PIN Entry: Full-screen numeric keypad, 4 digits, 3 attempts then 30-second lockout
 *
 * @param pinState Current PIN entry state
 * @param isSetupMode true for first-time setup, false for normal entry
 * @param onDigitClick Called when a digit button is pressed
 * @param onBackspaceClick Called when backspace is pressed
 * @param onClearClick Called when clear is pressed
 * @param onCancel Called when user wants to cancel and exit admin mode
 * @param modifier Modifier for the screen
 */
@Composable
fun PinEntryScreen(
        pinState: PinState,
        isSetupMode: Boolean,
        onDigitClick: (Int) -> Unit,
        onBackspaceClick: () -> Unit,
        onClearClick: () -> Unit,
        onCancel: () -> Unit,
        modifier: Modifier = Modifier
) {
    Box(
            modifier = modifier.fillMaxSize().background(DarkBackground),
            contentAlignment = Alignment.Center
    ) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                    text = getTitle(isSetupMode, pinState.isConfirmingPin),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                    text = getSubtitle(isSetupMode, pinState.isConfirmingPin),
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // PIN dots indicator
            PinDotIndicator(pinLength = pinState.enteredPin.length, maxLength = 4)

            Spacer(modifier = Modifier.height(16.dp))

            // Error message
            if (pinState.error != null) {
                Text(
                        text = pinState.error,
                        fontSize = 14.sp,
                        color = Color(0xFFEF5350), // Error red
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                )
            } else {
                // Placeholder to maintain layout
                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Numeric keypad
            NumericKeypad(
                    onDigitClick = { digit ->
                        if (!pinState.isLockedOut) {
                            onDigitClick(digit)
                        }
                    },
                    onBackspaceClick = onBackspaceClick,
                    onClearClick = onClearClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Cancel button
            TextButton(onClick = onCancel) {
                Text(text = "Cancel", fontSize = 16.sp, color = AccentBlue)
            }
        }
    }
}

private fun getTitle(isSetupMode: Boolean, isConfirming: Boolean): String {
    return when {
        isSetupMode && isConfirming -> "Confirm PIN"
        isSetupMode -> "Create Admin PIN"
        else -> "Enter Admin PIN"
    }
}

private fun getSubtitle(isSetupMode: Boolean, isConfirming: Boolean): String {
    return when {
        isSetupMode && isConfirming -> "Enter the same PIN again to confirm"
        isSetupMode -> "Choose a 4-digit PIN for admin access"
        else -> "Enter your 4-digit PIN"
    }
}

// region Previews

@Preview(
        name = "PIN Entry - Setup Mode",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 700
)
@Composable
private fun PinEntrySetupPreview() {
    MemoryLinkTheme {
        PinEntryScreen(
                pinState = PinState(enteredPin = "12"),
                isSetupMode = true,
                onDigitClick = {},
                onBackspaceClick = {},
                onClearClick = {},
                onCancel = {}
        )
    }
}

@Preview(
        name = "PIN Entry - Confirm Mode",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 700
)
@Composable
private fun PinEntryConfirmPreview() {
    MemoryLinkTheme {
        PinEntryScreen(
                pinState = PinState(enteredPin = "1", isConfirmingPin = true),
                isSetupMode = true,
                onDigitClick = {},
                onBackspaceClick = {},
                onClearClick = {},
                onCancel = {}
        )
    }
}

@Preview(
        name = "PIN Entry - Validation Mode",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 700
)
@Composable
private fun PinEntryValidationPreview() {
    MemoryLinkTheme {
        PinEntryScreen(
                pinState = PinState(enteredPin = "123"),
                isSetupMode = false,
                onDigitClick = {},
                onBackspaceClick = {},
                onClearClick = {},
                onCancel = {}
        )
    }
}

@Preview(
        name = "PIN Entry - Error",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 700
)
@Composable
private fun PinEntryErrorPreview() {
    MemoryLinkTheme {
        PinEntryScreen(
                pinState = PinState(enteredPin = "", error = "Incorrect PIN. 2 attempts left."),
                isSetupMode = false,
                onDigitClick = {},
                onBackspaceClick = {},
                onClearClick = {},
                onCancel = {}
        )
    }
}

@Preview(
        name = "PIN Entry - Locked Out",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 700
)
@Composable
private fun PinEntryLockedOutPreview() {
    MemoryLinkTheme {
        PinEntryScreen(
                pinState =
                        PinState(
                                enteredPin = "",
                                error = "Too many attempts. Wait 25 seconds.",
                                isLockedOut = true,
                                lockoutRemainingSeconds = 25
                        ),
                isSetupMode = false,
                onDigitClick = {},
                onBackspaceClick = {},
                onClearClick = {},
                onCancel = {}
        )
    }
}

// endregion
