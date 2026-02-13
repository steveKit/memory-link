package com.memorylink.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centralized display constants for the kiosk UI.
 *
 * All tweakable values for fonts, margins, and sizing are defined here for easy adjustment. This
 * allows quick iteration on visual design without hunting through multiple files.
 *
 * Design philosophy:
 * - Text should be as large as possible for elderly/sight-challenged users
 * - Maximum sizes prevent text from becoming awkwardly large on short content
 * - Minimum sizes ensure readability on any device
 */
object DisplayConstants {

    // ========== Font Size Limits ==========

    /**
     * Maximum font size for the clock time display.
     *
     * Time is sized independently to fill width as a single line, up to this maximum.
     */
    val MAX_TIME_FONT_SIZE: TextUnit = 130.sp

    /**
     * Maximum font size for date and event message text.
     *
     * Date fills width as a single line, event messages can wrap. Both are capped at this size.
     */
    val MAX_FONT_SIZE: TextUnit = 95.sp

    /**
     * Minimum font size for auto-sizing text.
     *
     * Ensures text never becomes too small to read, even with very long content.
     */
    val MIN_FONT_SIZE: TextUnit = 24.sp

    // ========== Layout Margins ==========

    /** Standard screen margin around content. */
    val SCREEN_MARGIN: Dp = 32.dp

    /** Margin for sleep mode (slightly larger for visual calm). */
    val SLEEP_MARGIN: Dp = 48.dp

    /** Spacing between time and date in clock display. */
    val CLOCK_VERTICAL_SPACING: Dp = 8.dp

    /** Spacing between clock and event in message mode. */
    val CLOCK_TO_EVENT_SPACING: Dp = 24.dp

    /** Padding inside event card. */
    val EVENT_CARD_PADDING: Dp = 12.dp

    /** Corner radius for event card. */
    val EVENT_CARD_CORNER_RADIUS: Dp = 16.dp

    // ========== Animation Durations ==========

    /**
     * Duration for state transition animations (e.g., sleep to wake).
     *
     * Per NFR-02: 1-second cross-fade for sleep transitions.
     */
    const val STATE_TRANSITION_DURATION_MS: Int = 1000

    // ========== Touch Targets ==========

    /**
     * Minimum touch target size for admin mode buttons.
     *
     * Per .clinerules/20-android.md: 64dp minimum for cognitive accessibility.
     */
    val MIN_TOUCH_TARGET: Dp = 64.dp
}
