package com.memorylink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memorylink.ui.theme.AccentBlue
import com.memorylink.ui.theme.DarkBackground
import com.memorylink.ui.theme.MemoryLinkTheme

/**
 * A touch-friendly numeric keypad for PIN entry.
 *
 * Per .clinerules/20-android.md:
 * - Touch Targets: Minimum 64dp for admin mode
 * - Large, clear buttons for easy interaction
 *
 * @param onDigitClick Called when a digit (0-9) is pressed
 * @param onBackspaceClick Called when backspace is pressed
 * @param onClearClick Called when clear is pressed (optional, shown only if provided)
 * @param modifier Modifier for the keypad container
 */
@Composable
fun NumericKeypad(
    onDigitClick: (Int) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: 1, 2, 3
        KeypadRow(
            keys = listOf(
                KeypadKey.Digit(1),
                KeypadKey.Digit(2),
                KeypadKey.Digit(3)
            ),
            onDigitClick = onDigitClick,
            onBackspaceClick = onBackspaceClick,
            onClearClick = onClearClick
        )

        // Row 2: 4, 5, 6
        KeypadRow(
            keys = listOf(
                KeypadKey.Digit(4),
                KeypadKey.Digit(5),
                KeypadKey.Digit(6)
            ),
            onDigitClick = onDigitClick,
            onBackspaceClick = onBackspaceClick,
            onClearClick = onClearClick
        )

        // Row 3: 7, 8, 9
        KeypadRow(
            keys = listOf(
                KeypadKey.Digit(7),
                KeypadKey.Digit(8),
                KeypadKey.Digit(9)
            ),
            onDigitClick = onDigitClick,
            onBackspaceClick = onBackspaceClick,
            onClearClick = onClearClick
        )

        // Row 4: Clear/Empty, 0, Backspace
        KeypadRow(
            keys = listOf(
                if (onClearClick != null) KeypadKey.Clear else KeypadKey.Empty,
                KeypadKey.Digit(0),
                KeypadKey.Backspace
            ),
            onDigitClick = onDigitClick,
            onBackspaceClick = onBackspaceClick,
            onClearClick = onClearClick
        )
    }
}

/** Represents different types of keys on the keypad. */
private sealed class KeypadKey {
    data class Digit(val value: Int) : KeypadKey()
    data object Backspace : KeypadKey()
    data object Clear : KeypadKey()
    data object Empty : KeypadKey()
}

@Composable
private fun KeypadRow(
    keys: List<KeypadKey>,
    onDigitClick: (Int) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: (() -> Unit)?
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { key ->
            when (key) {
                is KeypadKey.Digit -> {
                    DigitButton(
                        digit = key.value,
                        onClick = { onDigitClick(key.value) }
                    )
                }
                KeypadKey.Backspace -> {
                    ActionButton(
                        label = "⌫",
                        onClick = onBackspaceClick
                    )
                }
                KeypadKey.Clear -> {
                    ActionButton(
                        label = "C",
                        onClick = { onClearClick?.invoke() }
                    )
                }
                KeypadKey.Empty -> {
                    Spacer(modifier = Modifier.size(BUTTON_SIZE))
                }
            }
        }
    }
}

@Composable
private fun DigitButton(
    digit: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(BUTTON_SIZE)
            .clip(CircleShape)
            .background(ButtonBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(BUTTON_SIZE)
            .clip(CircleShape)
            .background(ActionButtonBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = AccentBlue
        )
    }
}

// Button size: 72dp to exceed 64dp minimum touch target
private val BUTTON_SIZE = 72.dp
private val ButtonBackground = Color(0xFF2A2A2A)
private val ActionButtonBackground = Color(0xFF1A1A1A)

/**
 * PIN dot indicator showing entry progress.
 *
 * @param pinLength Current number of digits entered
 * @param maxLength Maximum PIN length (default 4)
 * @param modifier Modifier for the row
 */
@Composable
fun PinDotIndicator(
    pinLength: Int,
    maxLength: Int = 4,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxLength) { index ->
            PinDot(filled = index < pinLength)
        }
    }
}

@Composable
private fun PinDot(
    filled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(
                if (filled) AccentBlue else Color(0xFF3A3A3A)
            )
    )
}

// region Previews

@Preview(
    name = "Numeric Keypad",
    showBackground = true,
    backgroundColor = 0xFF121212,
    widthDp = 400,
    heightDp = 450
)
@Composable
private fun NumericKeypadPreview() {
    MemoryLinkTheme {
        Column(
            modifier = Modifier
                .background(DarkBackground)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PinDotIndicator(pinLength = 2)
            Spacer(modifier = Modifier.height(32.dp))
            NumericKeypad(
                onDigitClick = {},
                onBackspaceClick = {},
                onClearClick = {}
            )
        }
    }
}

@Preview(
    name = "PIN Dots - Empty",
    showBackground = true,
    backgroundColor = 0xFF121212
)
@Composable
private fun PinDotsEmptyPreview() {
    MemoryLinkTheme {
        PinDotIndicator(
            pinLength = 0,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(
    name = "PIN Dots - Partial",
    showBackground = true,
    backgroundColor = 0xFF121212
)
@Composable
private fun PinDotsPartialPreview() {
    MemoryLinkTheme {
        PinDotIndicator(
            pinLength = 2,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(
    name = "PIN Dots - Full",
    showBackground = true,
    backgroundColor = 0xFF121212
)
@Composable
private fun PinDotsFullPreview() {
    MemoryLinkTheme {
        PinDotIndicator(
            pinLength = 4,
            modifier = Modifier.padding(16.dp)
        )
    }
}

// endregion
