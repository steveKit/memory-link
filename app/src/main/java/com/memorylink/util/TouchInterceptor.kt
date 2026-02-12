package com.memorylink.util

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.memorylink.ui.kiosk.AdminGestureConfig

/**
 * Touch interceptor for kiosk mode.
 *
 * In kiosk mode (normal display), all touch events should be blocked EXCEPT
 * for the admin gesture region (top-left corner). This prevents the memory
 * user from accidentally interacting with the display.
 *
 * Per .clinerules/20-android.md:
 * - Touch Disabled: Intercept all touch events in normal mode; only admin mode allows interaction
 *
 * The admin gesture (5 rapid taps in top-left 100x100dp region) is handled
 * separately by AdminGestureDetector and is allowed through.
 */
object TouchInterceptor {

    /**
     * Create a modifier that blocks all touch events except in the admin gesture region.
     *
     * This modifier should be applied to the root kiosk composable. It consumes
     * all pointer events to prevent them from propagating to child composables,
     * except for taps in the admin gesture region which are allowed through.
     *
     * @param adminRegionSize Size of the admin gesture tap region (default: 100dp)
     * @param enabled Whether touch blocking is enabled (false in admin mode)
     * @return Modifier that blocks touch outside admin region
     */
    @Composable
    fun createBlockingModifier(
        adminRegionSize: Dp = AdminGestureConfig.TAP_REGION_SIZE,
        enabled: Boolean = true
    ): Modifier {
        val density = LocalDensity.current
        val adminRegionPx = with(density) { adminRegionSize.toPx() }

        return if (enabled) {
            Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)

                        // Check each pointer in the event
                        event.changes.forEach { change: PointerInputChange ->
                            val position = change.position

                            // Allow touches in admin region (top-left corner)
                            val isInAdminRegion = position.x <= adminRegionPx &&
                                    position.y <= adminRegionPx

                            // Consume (block) touches outside admin region
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
    }
}

/**
 * Extension function to apply touch blocking to a composable.
 *
 * Blocks all touch events except in the top-left admin gesture region.
 * This is used in kiosk mode to prevent accidental interaction.
 *
 * @param enabled Whether touch blocking is enabled (default: true in kiosk mode)
 * @param adminRegionSize Size of the allowed admin tap region (default: 100dp)
 * @return Modified Modifier with touch blocking
 */
@Composable
fun Modifier.blockTouchExceptAdminRegion(
    enabled: Boolean = true,
    adminRegionSize: Dp = AdminGestureConfig.TAP_REGION_SIZE
): Modifier {
    return this.then(
        TouchInterceptor.createBlockingModifier(
            adminRegionSize = adminRegionSize,
            enabled = enabled
        )
    )
}
