package com.memorylink

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.memorylink.ui.admin.AdminNavGraph
import com.memorylink.ui.admin.AdminViewModel
import com.memorylink.ui.kiosk.AdminGestureState
import com.memorylink.ui.kiosk.KioskScreen
import com.memorylink.ui.kiosk.KioskViewModel
import com.memorylink.ui.kiosk.detectAdminGesture
import com.memorylink.ui.kiosk.rememberAdminGestureState
import com.memorylink.ui.theme.MemoryLinkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Main navigation routes. */
object MainRoutes {
    const val KIOSK = "kiosk"
    const val ADMIN = "admin"
}

/**
 * Single activity that hosts the entire Compose UI.
 *
 * Configured for kiosk mode: fullscreen, landscape, no system UI.
 *
 * Handles:
 * - Kiosk display mode (default)
 * - Admin mode navigation (via 5-tap gesture)
 * - Google Sign-In activity result
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Callback to pass sign-in result to ViewModel. */
    private var signInResultCallback: ((Intent?) -> Unit)? = null

    /** Google Sign-In activity launcher. */
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register sign-in activity result handler
        signInLauncher =
                registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
                    ->
                    signInResultCallback?.invoke(result.data)
                }

        // Keep screen on while app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Enable edge-to-edge display
        enableEdgeToEdge()

        setContent {
            MemoryLinkTheme {
                MemoryLinkNavHost(
                        onSignInRequested = { intent, callback ->
                            signInResultCallback = callback
                            signInLauncher.launch(intent)
                        }
                )
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

/**
 * Main navigation host for the app.
 *
 * Routes:
 * - kiosk: Main display screen (default)
 * - admin: Admin mode screens
 *
 * @param onSignInRequested Callback to launch Google Sign-In activity
 */
@Composable
private fun MemoryLinkNavHost(onSignInRequested: (Intent, (Intent?) -> Unit) -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = MainRoutes.KIOSK) {
        composable(MainRoutes.KIOSK) {
            KioskWithAdminGesture(
                    onAdminGestureDetected = {
                        navController.navigate(MainRoutes.ADMIN) {
                            // Don't add to back stack to prevent back button issues
                            launchSingleTop = true
                        }
                    }
            )
        }

        composable(MainRoutes.ADMIN) {
            val adminViewModel: AdminViewModel = hiltViewModel()
            val scope = rememberCoroutineScope()

            // Track sign-in result
            var pendingSignInResult by remember { mutableStateOf<Intent?>(null) }

            // Handle sign-in result when it arrives
            LaunchedEffect(pendingSignInResult) {
                pendingSignInResult?.let { data ->
                    adminViewModel.handleSignInResult(data)
                    pendingSignInResult = null
                }
            }

            AdminNavGraph(
                    viewModel = adminViewModel,
                    onSignInRequested = {
                        val intent = adminViewModel.getSignInIntent()
                        onSignInRequested(intent) { resultData ->
                            scope.launch { adminViewModel.handleSignInResult(resultData) }
                        }
                    },
                    onExitAdmin = {
                        navController.navigate(MainRoutes.KIOSK) {
                            popUpTo(MainRoutes.KIOSK) { inclusive = true }
                        }
                    }
            )
        }
    }
}

/**
 * Kiosk screen with admin gesture detection overlay.
 *
 * Detects 5 rapid taps in top-left corner to trigger admin mode.
 *
 * @param onAdminGestureDetected Called when admin gesture is completed
 */
@Composable
private fun KioskWithAdminGesture(onAdminGestureDetected: () -> Unit) {
    val kioskViewModel: KioskViewModel = hiltViewModel()
    val displayState by kioskViewModel.displayState.collectAsStateWithLifecycle()
    val gestureState: AdminGestureState = rememberAdminGestureState()

    KioskScreen(
            displayState = displayState,
            modifier =
                    Modifier.fillMaxSize()
                            .detectAdminGesture(
                                    gestureState = gestureState,
                                    onAdminGestureDetected = onAdminGestureDetected
                            )
    )
}
