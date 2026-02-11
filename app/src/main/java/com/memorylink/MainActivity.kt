package com.memorylink

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorylink.ui.kiosk.KioskScreen
import com.memorylink.ui.kiosk.KioskViewModel
import com.memorylink.ui.theme.MemoryLinkTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity that hosts the entire Compose UI. Configured for kiosk mode: fullscreen,
 * landscape, no system UI.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Enable edge-to-edge display
        enableEdgeToEdge()

        setContent {
            MemoryLinkTheme {
                val viewModel: KioskViewModel = hiltViewModel()
                val displayState = viewModel.displayState.collectAsStateWithLifecycle()
                KioskScreen(displayState = displayState.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Hide system UI for kiosk mode
        hideSystemUI()
    }

    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
                (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }
}
