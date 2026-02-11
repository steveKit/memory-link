package com.memorylink.ui.kiosk

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Configuration for the admin gesture detector.
 *
 * Per .clinerules/20-android.md:
 * - Trigger: 5 rapid taps in top-left 100x100dp region within 2 seconds
 */
object AdminGestureConfig {
    /** Size of the tap target region in the top-left corner. */
    val TAP_REGION_SIZE = 100.dp

    /** Number of taps required to trigger admin mode. */
    const val REQUIRED_TAPS = 5

    /** Maximum time window for all taps (milliseconds). */
    const val TAP_WINDOW_MS = 2000L
}

/**
 * State holder for tracking admin gesture taps.
 */
class AdminGestureState {
    var tapCount by mutableStateOf(0)
        private set

    var firstTapTime by mutableLongStateOf(0L)
        private set

    /**
     * Record a tap and check if gesture is complete.
     *
     * @return true if the admin gesture is triggered (5 taps completed)
     */
    fun recordTap(): Boolean {
        val now = System.currentTimeMillis()

        // Reset if too much time has passed since first tap
        if (tapCount > 0 && (now - firstTapTime) > AdminGestureConfig.TAP_WINDOW_MS) {
            reset()
        }

        // Record first tap time
        if (tapCount == 0) {
            firstTapTime = now
        }

        tapCount++

        // Check if gesture is complete
        return if (tapCount >= AdminGestureConfig.REQUIRED_TAPS) {
            reset()
            true
        } else {
            false
        }
    }

    /** Reset the gesture state. */
    fun reset() {
        tapCount = 0
        firstTapTime = 0L
    }
}

/**
 * Remember an AdminGestureState for tracking the admin unlock gesture.
 */
@Composable
fun rememberAdminGestureState(): AdminGestureState {
    return remember { AdminGestureState() }
}

/**
 * Modifier that detects the admin gesture (5 rapid taps in top-left corner).
 *
 * This modifier should be applied to a full-screen composable. It only responds
 * to taps within the top-left 100x100dp region.
 *
 * Per .clinerules/20-android.md:
 * - Admin Trigger: 5 rapid taps in top-left 100x100dp region within 2 seconds
 *
 * @param gestureState The state object tracking tap count and timing
 * @param onAdminGestureDetected Callback when the gesture is successfully completed
 * @return Modified Modifier with gesture detection
 */
@Composable
fun Modifier.detectAdminGesture(
    gestureState: AdminGestureState,
    onAdminGestureDetected: () -> Unit
): Modifier {
    val density = LocalDensity.current
    val tapRegionPx = with(density) { AdminGestureConfig.TAP_REGION_SIZE.toPx() }

    return this.pointerInput(Unit) {
        detectTapGestures { offset: Offset ->
            // Check if tap is within the top-left corner region
            if (offset.x <= tapRegionPx && offset.y <= tapRegionPx) {
                val gestureTriggered = gestureState.recordTap()
                if (gestureTriggered) {
                    onAdminGestureDetected()
                }
            } else {
                // Tap outside region resets the count
                gestureState.reset()
            }
        }
    }
}
