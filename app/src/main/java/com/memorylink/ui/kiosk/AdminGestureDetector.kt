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
import androidx.compose.ui.input.pointer.PointerEventPass
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

/** State holder for tracking admin gesture taps. */
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

/** Remember an AdminGestureState for tracking the admin unlock gesture. */
@Composable
fun rememberAdminGestureState(): AdminGestureState {
    return remember { AdminGestureState() }
}

/**
 * Modifier that detects the admin gesture (5 rapid taps in top-left corner).
 *
 * This modifier should be applied to a full-screen composable. It:
 * 1. Blocks ALL touch events outside the admin region (kiosk lock)
 * 2. Detects the 5-tap admin gesture in the top-left 100x100dp region
 *
 * Per .clinerules/20-android.md:
 * - Admin Trigger: 5 rapid taps in top-left 100x100dp region within 2 seconds
 * - Touch Disabled: Intercept all touch events in normal mode; only admin mode allows interaction
 *
 * @param gestureState The state object tracking tap count and timing
 * @param onAdminGestureDetected Callback when the gesture is successfully completed
 * @param blockTouches If true, blocks all touches outside admin region (default: true for kiosk
 * mode)
 * @return Modified Modifier with gesture detection and touch blocking
 */
@Composable
fun Modifier.detectAdminGesture(
        gestureState: AdminGestureState,
        onAdminGestureDetected: () -> Unit,
        blockTouches: Boolean = true
): Modifier {
    val density = LocalDensity.current
    val tapRegionPx = with(density) { AdminGestureConfig.TAP_REGION_SIZE.toPx() }

    // Apply touch blocking modifier first (at Initial pass to intercept early)
    val blockingModifier =
            if (blockTouches) {
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            // Consume all touch events outside admin region to block interaction
                            event.changes.forEach { change ->
                                val position = change.position
                                val isInAdminRegion =
                                        position.x <= tapRegionPx && position.y <= tapRegionPx
                                if (!isInAdminRegion) {
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            } else {
                Modifier
            }

    // Apply tap gesture detector for admin region
    val gestureModifier =
            Modifier.pointerInput(Unit) {
                detectTapGestures { offset: Offset ->
                    // Only process taps in the admin region
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

    return this.then(blockingModifier).then(gestureModifier)
}
