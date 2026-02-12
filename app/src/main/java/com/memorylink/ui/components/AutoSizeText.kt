package com.memorylink.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.memorylink.ui.theme.MemoryLinkTheme
import com.memorylink.ui.theme.TextPrimary

/**
 * Auto-sizing text component that maximizes font size to fill available space.
 *
 * Uses binary search with TextMeasurer to find the optimal font size that fits within the container
 * without overflow. Text wraps naturally by default (soft wrap enabled).
 *
 * Designed for elderly/sight-challenged users - text is always as large as possible.
 *
 * @param text The text to display
 * @param modifier Modifier for the container (should include size constraints)
 * @param style Base text style (font weight, color, etc.) - fontSize will be overridden
 * @param maxFontSize Maximum font size to try (default: 300sp for very large displays)
 * @param minFontSize Minimum font size (default: 24sp for readability)
 * @param textAlign Text alignment within the container
 * @param contentAlignment Alignment of the text block within the container
 * @param softWrap Whether text should wrap to multiple lines (default: true)
 */
@Composable
fun AutoSizeText(
        text: String,
        modifier: Modifier = Modifier,
        style: TextStyle = LocalTextStyle.current,
        maxFontSize: TextUnit = 300.sp,
        minFontSize: TextUnit = 24.sp,
        textAlign: TextAlign = TextAlign.Center,
        contentAlignment: Alignment = Alignment.Center,
        softWrap: Boolean = true
) {
        BoxWithConstraints(modifier = modifier, contentAlignment = contentAlignment) {
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current

                val maxWidthPx = constraints.maxWidth
                val maxHeightPx = constraints.maxHeight

                val optimalFontSize =
                        remember(
                                text,
                                maxWidthPx,
                                maxHeightPx,
                                style,
                                maxFontSize,
                                minFontSize,
                                softWrap
                        ) {
                                findOptimalFontSize(
                                        text = text,
                                        textMeasurer = textMeasurer,
                                        maxWidthPx = maxWidthPx,
                                        maxHeightPx = maxHeightPx,
                                        minFontSizeSp =
                                                with(density) {
                                                        minFontSize.toPx() / this.fontScale
                                                },
                                        maxFontSizeSp =
                                                with(density) {
                                                        maxFontSize.toPx() / this.fontScale
                                                },
                                        baseStyle = style,
                                        density = density,
                                        softWrap = softWrap
                                )
                        }

                Text(
                        text = text,
                        style =
                                style.copy(
                                        fontSize = optimalFontSize,
                                        lineHeight = optimalFontSize * 1.15f
                                ),
                        textAlign = textAlign,
                        softWrap = softWrap
                )
        }
}

/**
 * Auto-sizing text component for AnnotatedString with multiple styles/colors.
 *
 * Same as the String version but accepts AnnotatedString for rich text formatting. Useful for
 * displaying text with multiple colors (e.g., "At 10:30 AM, Event Title" where the time is a
 * different color than the title).
 *
 * @param text The AnnotatedString to display
 * @param modifier Modifier for the container
 * @param style Base text style - fontSize will be overridden
 * @param maxFontSize Maximum font size to try
 * @param minFontSize Minimum font size
 * @param textAlign Text alignment within the container
 * @param contentAlignment Alignment of the text block within the container
 * @param softWrap Whether text should wrap to multiple lines
 */
@Composable
fun AutoSizeText(
        text: androidx.compose.ui.text.AnnotatedString,
        modifier: Modifier = Modifier,
        style: TextStyle = LocalTextStyle.current,
        maxFontSize: TextUnit = 300.sp,
        minFontSize: TextUnit = 24.sp,
        textAlign: TextAlign = TextAlign.Center,
        contentAlignment: Alignment = Alignment.Center,
        softWrap: Boolean = true
) {
        BoxWithConstraints(modifier = modifier, contentAlignment = contentAlignment) {
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current

                val maxWidthPx = constraints.maxWidth
                val maxHeightPx = constraints.maxHeight

                val optimalFontSize =
                        remember(
                                text,
                                maxWidthPx,
                                maxHeightPx,
                                style,
                                maxFontSize,
                                minFontSize,
                                softWrap
                        ) {
                                findOptimalFontSizeAnnotated(
                                        text = text,
                                        textMeasurer = textMeasurer,
                                        maxWidthPx = maxWidthPx,
                                        maxHeightPx = maxHeightPx,
                                        minFontSizeSp =
                                                with(density) {
                                                        minFontSize.toPx() / this.fontScale
                                                },
                                        maxFontSizeSp =
                                                with(density) {
                                                        maxFontSize.toPx() / this.fontScale
                                                },
                                        baseStyle = style,
                                        density = density,
                                        softWrap = softWrap
                                )
                        }

                Text(
                        text = text,
                        style =
                                style.copy(
                                        fontSize = optimalFontSize,
                                        lineHeight = optimalFontSize * 1.15f
                                ),
                        textAlign = textAlign,
                        softWrap = softWrap
                )
        }
}

/**
 * Binary search to find the maximum font size that fits within constraints.
 *
 * @return The optimal font size in sp
 */
private fun findOptimalFontSize(
        text: String,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        maxWidthPx: Int,
        maxHeightPx: Int,
        minFontSizeSp: Float,
        maxFontSizeSp: Float,
        baseStyle: TextStyle,
        density: androidx.compose.ui.unit.Density,
        softWrap: Boolean = true
): TextUnit {
        // Handle edge cases
        if (text.isEmpty()) return maxFontSizeSp.sp
        if (maxWidthPx <= 0 || maxHeightPx <= 0) return minFontSizeSp.sp

        var low = minFontSizeSp
        var high = maxFontSizeSp
        var result = minFontSizeSp

        // Binary search with 0.5sp precision
        while (high - low > 0.5f) {
                val mid = (low + high) / 2f

                val testStyle = baseStyle.copy(fontSize = mid.sp, lineHeight = (mid * 1.15f).sp)

                val measureResult =
                        textMeasurer.measure(
                                text = text,
                                style = testStyle,
                                constraints =
                                        if (softWrap) {
                                                Constraints(
                                                        maxWidth = maxWidthPx,
                                                        maxHeight = Int.MAX_VALUE // Let it wrap
                                                        // naturally
                                                        )
                                        } else {
                                                // No wrap - use infinite width to measure single
                                                // line
                                                Constraints(
                                                        maxWidth = Int.MAX_VALUE,
                                                        maxHeight = Int.MAX_VALUE
                                                )
                                        }
                        )

                // Check if text fits within both width and height
                val fitsWidth = measureResult.size.width <= maxWidthPx
                val fitsHeight = measureResult.size.height <= maxHeightPx

                if (fitsWidth && fitsHeight) {
                        result = mid
                        low = mid
                } else {
                        high = mid
                }
        }

        return result.sp
}

/**
 * Binary search to find the maximum font size for AnnotatedString.
 *
 * @return The optimal font size in sp
 */
private fun findOptimalFontSizeAnnotated(
        text: androidx.compose.ui.text.AnnotatedString,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        maxWidthPx: Int,
        maxHeightPx: Int,
        minFontSizeSp: Float,
        maxFontSizeSp: Float,
        baseStyle: TextStyle,
        density: androidx.compose.ui.unit.Density,
        softWrap: Boolean = true
): TextUnit {
        // Handle edge cases
        if (text.isEmpty()) return maxFontSizeSp.sp
        if (maxWidthPx <= 0 || maxHeightPx <= 0) return minFontSizeSp.sp

        var low = minFontSizeSp
        var high = maxFontSizeSp
        var result = minFontSizeSp

        // Binary search with 0.5sp precision
        while (high - low > 0.5f) {
                val mid = (low + high) / 2f

                val testStyle = baseStyle.copy(fontSize = mid.sp, lineHeight = (mid * 1.15f).sp)

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

        return result.sp
}

// region Previews

@Preview(
        name = "AutoSizeText - Short",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 200
)
@Composable
private fun AutoSizeTextShortPreview() {
        MemoryLinkTheme {
                AutoSizeText(
                        text = "Hello",
                        modifier = Modifier.fillMaxSize(),
                        style = TextStyle(color = TextPrimary)
                )
        }
}

@Preview(
        name = "AutoSizeText - Medium",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 200
)
@Composable
private fun AutoSizeTextMediumPreview() {
        MemoryLinkTheme {
                AutoSizeText(
                        text = "At 3:00 PM, Doctor Appointment",
                        modifier = Modifier.fillMaxSize(),
                        style = TextStyle(color = TextPrimary)
                )
        }
}

@Preview(
        name = "AutoSizeText - Long",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 400,
        heightDp = 200
)
@Composable
private fun AutoSizeTextLongPreview() {
        MemoryLinkTheme {
                AutoSizeText(
                        text =
                                "At 3:00 PM, Meet Eric downstairs so he can take you to your doctors appointment",
                        modifier = Modifier.fillMaxSize(),
                        style = TextStyle(color = TextPrimary)
                )
        }
}

@Preview(
        name = "AutoSizeText - Clock Time",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 600,
        heightDp = 150
)
@Composable
private fun AutoSizeTextClockPreview() {
        MemoryLinkTheme {
                AutoSizeText(
                        text = "2:30 PM",
                        modifier = Modifier.fillMaxSize(),
                        style =
                                TextStyle(
                                        color = TextPrimary,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                )
        }
}

@Preview(
        name = "AutoSizeText - Tablet Landscape",
        showBackground = true,
        backgroundColor = 0xFF121212,
        widthDp = 800,
        heightDp = 300
)
@Composable
private fun AutoSizeTextTabletPreview() {
        MemoryLinkTheme {
                AutoSizeText(
                        text = "At 10:30 AM, Family Dinner at Sarah's House",
                        modifier = Modifier.fillMaxSize(),
                        style = TextStyle(color = TextPrimary)
                )
        }
}

// endregion
