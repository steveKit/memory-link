package com.memorylink.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorylink.ui.components.NumericKeypad
import com.memorylink.ui.components.PinDotIndicator
import com.memorylink.ui.theme.AccentBlue
import com.memorylink.ui.theme.DarkBackground
import com.memorylink.ui.theme.MemoryLinkTheme
import kotlinx.coroutines.delay

/**
 * Setup Wizard Screen - First-time setup flow after QR provisioning.
 *
 * Three steps:
 * 1. Create admin PIN
 * 2. Sign in to Google
 * 3. Select calendar
 *
 * @param viewModel SetupWizardViewModel for managing wizard state
 * @param onSignInRequested Callback to launch Google Sign-In from Activity
 * @param onSetupComplete Called when setup is finished, navigate to kiosk
 * @param modifier Modifier for the screen
 */
@Composable
fun SetupWizardScreen(
        viewModel: SetupWizardViewModel = hiltViewModel(),
        onSignInRequested: () -> Unit,
        onSetupComplete: () -> Unit,
        modifier: Modifier = Modifier
) {
        val wizardState by viewModel.wizardState.collectAsStateWithLifecycle()
        val pinState by viewModel.pinState.collectAsStateWithLifecycle()
        val authState by viewModel.authState.collectAsStateWithLifecycle()
        val calendarState by viewModel.calendarState.collectAsStateWithLifecycle()

        // Handle setup completion with brief success message
        if (wizardState.isSetupComplete) {
                SetupCompleteScreen(onComplete = onSetupComplete)
                return
        }

        Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
                Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        // Header with app name
                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                                text = "Welcome to MemoryLink",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Step indicator
                        StepIndicator(
                                currentStep = wizardState.currentStep,
                                totalSteps = wizardState.totalSteps
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Content area - switches based on current step
                        AnimatedContent(
                                targetState = wizardState.currentStep,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "wizard_step"
                        ) { step ->
                                when (step) {
                                        1 ->
                                                SetupPinContent(
                                                        pinState = pinState,
                                                        onDigitClick = viewModel::addPinDigit,
                                                        onBackspaceClick =
                                                                viewModel::removePinDigit,
                                                        onClearClick = viewModel::clearPin
                                                )
                                        2 ->
                                                SetupGoogleSignInContent(
                                                        authState = authState,
                                                        onSignInClick = onSignInRequested
                                                )
                                        3 ->
                                                SetupCalendarContent(
                                                        calendarState = calendarState,
                                                        onCalendarSelected =
                                                                viewModel::selectCalendar,
                                                        onRefresh = viewModel::loadCalendars
                                                )
                                }
                        }
                }
        }
}

// ========== Step Indicator ==========

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int, modifier: Modifier = Modifier) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                        text = "Step $currentStep of $totalSteps",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(totalSteps) { index ->
                                val stepNumber = index + 1
                                StepDot(
                                        isCompleted = stepNumber < currentStep,
                                        isCurrent = stepNumber == currentStep
                                )
                        }
                }
        }
}

@Composable
private fun StepDot(isCompleted: Boolean, isCurrent: Boolean, modifier: Modifier = Modifier) {
        val color =
                when {
                        isCompleted -> AccentBlue
                        isCurrent -> Color.White
                        else -> Color.White.copy(alpha = 0.3f)
                }

        val size = if (isCurrent) 12.dp else 10.dp

        Box(
                modifier =
                        modifier.size(size).clip(CircleShape).background(color).let {
                                if (isCurrent) {
                                        it.background(Color.Transparent)
                                                .clip(CircleShape)
                                                .background(color.copy(alpha = 0.3f))
                                                .padding(2.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                } else {
                                        it
                                }
                        }
        )
}

// ========== Step 1: PIN Creation ==========

@Composable
private fun SetupPinContent(
        pinState: SetupPinState,
        onDigitClick: (Int) -> Unit,
        onBackspaceClick: () -> Unit,
        onClearClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
                // Title
                Text(
                        text = if (pinState.isConfirmingPin) "Confirm PIN" else "Create Admin PIN",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                        text =
                                if (pinState.isConfirmingPin) "Enter the same PIN again to confirm"
                                else "Choose a 4-digit PIN for admin access",
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
                                color = Color(0xFFEF5350),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                        )
                } else {
                        Spacer(modifier = Modifier.height(20.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Numeric keypad
                NumericKeypad(
                        onDigitClick = onDigitClick,
                        onBackspaceClick = onBackspaceClick,
                        onClearClick = onClearClick
                )
        }
}

// ========== Step 2: Google Sign-In ==========

@Composable
private fun SetupGoogleSignInContent(
        authState: SetupAuthState,
        onSignInClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
                // Title
                Text(
                        text = "Sign in to Google",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text = "Connect your Google account to\nsync calendar events",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                if (authState.isLoading) {
                        CircularProgressIndicator(
                                color = AccentBlue,
                                modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                                text = "Signing in...",
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.7f)
                        )
                } else {
                        // Google logo placeholder
                        Box(
                                modifier =
                                        Modifier.size(80.dp)
                                                .clip(CircleShape)
                                                .background(Color.White),
                                contentAlignment = Alignment.Center
                        ) {
                                Text(
                                        text = "G",
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4285F4)
                                )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        if (authState.error != null) {
                                Text(
                                        text = authState.error,
                                        fontSize = 14.sp,
                                        color = Color(0xFFEF5350),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Sign in button
                        Button(
                                onClick = onSignInClick,
                                modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
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
}

// ========== Step 3: Calendar Selection ==========

@Composable
private fun SetupCalendarContent(
        calendarState: SetupCalendarState,
        onCalendarSelected: (String, String) -> Unit,
        onRefresh: () -> Unit,
        modifier: Modifier = Modifier
) {
        Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // Title
                Text(
                        text = "Select Calendar",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text = "Choose which calendar to display events from",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                when {
                        calendarState.isLoading -> {
                                Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                ) {
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
                                Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                ) {
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
                                                        Text(
                                                                text = "Try Again",
                                                                fontSize = 16.sp,
                                                                color = AccentBlue
                                                        )
                                                }
                                        }
                                }
                        }
                        calendarState.calendars.isEmpty() -> {
                                Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(text = "📅", fontSize = 48.sp)
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                        text = "No calendars found",
                                                        fontSize = 16.sp,
                                                        color = Color.White.copy(alpha = 0.7f)
                                                )
                                                Spacer(modifier = Modifier.height(24.dp))
                                                TextButton(onClick = onRefresh) {
                                                        Text(
                                                                text = "Refresh",
                                                                fontSize = 16.sp,
                                                                color = AccentBlue
                                                        )
                                                }
                                        }
                                }
                        }
                        else -> {
                                LazyColumn(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        items(calendarState.calendars) { calendar ->
                                                SetupCalendarListItem(
                                                        calendar = calendar,
                                                        isSelected =
                                                                calendar.id ==
                                                                        calendarState
                                                                                .selectedCalendarId,
                                                        onClick = {
                                                                onCalendarSelected(
                                                                        calendar.id,
                                                                        calendar.name
                                                                )
                                                        }
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun SetupCalendarListItem(
        calendar: SetupCalendarItem,
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
                                        .background(
                                                if (isSelected) AccentBlue else Color(0xFF3A3A3A)
                                        ),
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
                                Text(
                                        text = "Primary calendar",
                                        fontSize = 12.sp,
                                        color = AccentBlue
                                )
                        }
                }
        }
}

// ========== Setup Complete Screen ==========

@Composable
private fun SetupCompleteScreen(onComplete: () -> Unit, modifier: Modifier = Modifier) {
        // Auto-navigate after 2 seconds
        LaunchedEffect(Unit) {
                delay(2000)
                onComplete()
        }

        Box(
                modifier = modifier.fillMaxSize().background(DarkBackground),
                contentAlignment = Alignment.Center
        ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "✓", fontSize = 72.sp, color = AccentBlue)

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                                text = "Setup Complete!",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                                text = "Starting MemoryLink...",
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.7f)
                        )
                }
        }
}

// ========== Previews ==========

@Preview(
        name = "Setup Wizard - Step 1 (PIN)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 700
)
@Composable
private fun SetupWizardStep1Preview() {
        MemoryLinkTheme {
                Box(modifier = Modifier.fillMaxSize().background(DarkBackground).padding(24.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(32.dp))
                                Text(
                                        text = "Welcome to MemoryLink",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                StepIndicator(currentStep = 1, totalSteps = 3)
                                Spacer(modifier = Modifier.height(32.dp))
                                SetupPinContent(
                                        pinState = SetupPinState(enteredPin = "12"),
                                        onDigitClick = {},
                                        onBackspaceClick = {},
                                        onClearClick = {}
                                )
                        }
                }
        }
}

@Preview(
        name = "Setup Wizard - Step 2 (Google)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 700
)
@Composable
private fun SetupWizardStep2Preview() {
        MemoryLinkTheme {
                Box(modifier = Modifier.fillMaxSize().background(DarkBackground).padding(24.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(32.dp))
                                Text(
                                        text = "Welcome to MemoryLink",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                StepIndicator(currentStep = 2, totalSteps = 3)
                                Spacer(modifier = Modifier.height(32.dp))
                                SetupGoogleSignInContent(
                                        authState = SetupAuthState(),
                                        onSignInClick = {}
                                )
                        }
                }
        }
}

@Preview(
        name = "Setup Wizard - Step 3 (Calendar)",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 700
)
@Composable
private fun SetupWizardStep3Preview() {
        MemoryLinkTheme {
                Box(modifier = Modifier.fillMaxSize().background(DarkBackground).padding(24.dp)) {
                        Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Spacer(modifier = Modifier.height(32.dp))
                                Text(
                                        text = "Welcome to MemoryLink",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                StepIndicator(currentStep = 3, totalSteps = 3)
                                Spacer(modifier = Modifier.height(32.dp))
                                SetupCalendarContent(
                                        calendarState =
                                                SetupCalendarState(
                                                        calendars =
                                                                listOf(
                                                                        SetupCalendarItem(
                                                                                "1",
                                                                                "Grandma's Calendar",
                                                                                false
                                                                        ),
                                                                        SetupCalendarItem(
                                                                                "2",
                                                                                "family@example.com",
                                                                                true
                                                                        ),
                                                                        SetupCalendarItem(
                                                                                "3",
                                                                                "Birthdays",
                                                                                false
                                                                        )
                                                                ),
                                                        selectedCalendarId = "1"
                                                ),
                                        onCalendarSelected = { _, _ -> },
                                        onRefresh = {}
                                )
                        }
                }
        }
}

@Preview(
        name = "Setup Complete",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 700
)
@Composable
private fun SetupCompletePreview() {
        MemoryLinkTheme {
                Box(
                        modifier = Modifier.fillMaxSize().background(DarkBackground),
                        contentAlignment = Alignment.Center
                ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "✓", fontSize = 72.sp, color = AccentBlue)
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                        text = "Setup Complete!",
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                        text = "Starting MemoryLink...",
                                        fontSize = 16.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                )
                        }
                }
        }
}
