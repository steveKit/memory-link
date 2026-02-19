package com.memorylink

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.memorylink.service.DeviceAdminReceiver
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
    const val SETUP = "setup"
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
 * - LockTask mode for kiosk lock (when device owner)
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    /** Token storage for checking setup state. */
    @javax.inject.Inject lateinit var tokenStorage: com.memorylink.data.auth.TokenStorage

    /** Callback to pass sign-in result to ViewModel. */
    private var signInResultCallback: ((Intent?) -> Unit)? = null

    /** Google Sign-In activity launcher. */
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    /** Device policy manager for kiosk mode. */
    private lateinit var devicePolicyManager: DevicePolicyManager

    /** Admin component name for device owner checks. */
    private lateinit var adminComponentName: ComponentName

    /** Whether this app is the device owner (can use LockTask). */
    private var isDeviceOwner = false

    /** Whether LockTask is currently active. */
    private var isLockTaskActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize device policy manager
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, DeviceAdminReceiver::class.java)
        isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(packageName)

        Log.d(TAG, "Device owner status: $isDeviceOwner")

        // If device owner, set up LockTask packages
        if (isDeviceOwner) {
            setupLockTaskPackages()
        }

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

        // Check if setup is complete to determine start destination
        val isSetupComplete = tokenStorage.isSetupComplete
        Log.d(TAG, "Setup complete: $isSetupComplete")

        setContent {
            MemoryLinkTheme {
                MemoryLinkNavHost(
                        isSetupComplete = isSetupComplete,
                        onSignInRequested = { intent, callback ->
                            signInResultCallback = callback
                            signInLauncher.launch(intent)
                        },
                        onEnterKioskMode = { startLockTaskIfOwner() },
                        onExitKioskMode = { stopLockTaskIfOwner() },
                        onExitApp = {
                            stopLockTaskIfOwner()
                            finishAffinity()
                        }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Hide system UI for kiosk mode
        hideSystemUI()

        // Start foreground service for calendar sync if setup is complete
        // The service handles 15-minute sync intervals and 1-minute state refresh
        if (tokenStorage.isSetupComplete) {
            Log.d(TAG, "Starting KioskForegroundService on resume")
            com.memorylink.service.KioskForegroundService.start(this)
        }
    }

    /**
     * Set up which packages are allowed in LockTask mode. Must be called when app is device owner.
     */
    private fun setupLockTaskPackages() {
        try {
            // Allow only this app in LockTask mode
            devicePolicyManager.setLockTaskPackages(adminComponentName, arrayOf(packageName))
            Log.d(TAG, "LockTask packages configured")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to set LockTask packages", e)
        }
    }

    /**
     * Start LockTask mode (kiosk lock) if this app is device owner. Blocks home button, recent
     * apps, and other escape routes.
     */
    fun startLockTaskIfOwner() {
        if (isDeviceOwner && !isLockTaskActive) {
            try {
                startLockTask()
                isLockTaskActive = true
                Log.d(TAG, "LockTask mode started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start LockTask", e)
            }
        } else if (!isDeviceOwner) {
            Log.d(TAG, "Not device owner, skipping LockTask start")
        }
    }

    /**
     * Stop LockTask mode (exit kiosk lock) if currently active. Called when entering admin mode.
     */
    fun stopLockTaskIfOwner() {
        if (isLockTaskActive) {
            try {
                stopLockTask()
                isLockTaskActive = false
                Log.d(TAG, "LockTask mode stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop LockTask", e)
            }
        }
    }

    /**
     * Check if this app is configured as device owner.
     * @return true if device owner, false otherwise
     */
    fun isDeviceOwner(): Boolean = isDeviceOwner

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
 * - setup: First-time setup wizard (shown when not configured)
 * - kiosk: Main display screen (default when setup complete)
 * - admin: Admin mode screens
 *
 * @param isSetupComplete Whether first-time setup has been completed
 * @param onSignInRequested Callback to launch Google Sign-In activity
 * @param onEnterKioskMode Callback to start LockTask when entering kiosk
 * @param onExitKioskMode Callback to stop LockTask when entering admin
 * @param onExitApp Callback to exit the app entirely
 */
@Composable
private fun MemoryLinkNavHost(
        isSetupComplete: Boolean,
        onSignInRequested: (Intent, (Intent?) -> Unit) -> Unit,
        onEnterKioskMode: () -> Unit,
        onExitKioskMode: () -> Unit,
        onExitApp: () -> Unit
) {
    val navController = rememberNavController()

    // Determine start destination based on setup state
    val startDestination = if (isSetupComplete) MainRoutes.KIOSK else MainRoutes.SETUP

    NavHost(navController = navController, startDestination = startDestination) {
        // Setup Wizard - First-time setup flow
        composable(MainRoutes.SETUP) {
            val setupViewModel: com.memorylink.ui.setup.SetupWizardViewModel = hiltViewModel()
            val scope = rememberCoroutineScope()

            com.memorylink.ui.setup.SetupWizardScreen(
                    viewModel = setupViewModel,
                    onSignInRequested = {
                        val intent = setupViewModel.getSignInIntent()
                        onSignInRequested(intent) { resultData ->
                            scope.launch { setupViewModel.handleSignInResult(resultData) }
                        }
                    },
                    onSetupComplete = {
                        // Navigate to kiosk mode after setup
                        onEnterKioskMode()
                        navController.navigate(MainRoutes.KIOSK) {
                            popUpTo(MainRoutes.SETUP) { inclusive = true }
                        }
                    }
            )
        }

        // Kiosk Mode - Main display
        composable(MainRoutes.KIOSK) {
            // Enable LockTask when entering/returning to kiosk
            LaunchedEffect(Unit) { onEnterKioskMode() }

            KioskWithAdminGesture(
                    onAdminGestureDetected = {
                        // Stop LockTask before navigating to admin
                        onExitKioskMode()
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
            val settingsState by adminViewModel.settingsState.collectAsStateWithLifecycle()

            // Track sign-in result
            var pendingSignInResult by remember { mutableStateOf<Intent?>(null) }

            // Handle sign-in result when it arrives
            LaunchedEffect(pendingSignInResult) {
                pendingSignInResult?.let { data ->
                    adminViewModel.handleSignInResult(data)
                    pendingSignInResult = null
                }
            }

            // Apply brightness in Admin mode
            // Uses configured brightness (not sleep-adjusted) so admin is always visible
            val view = LocalView.current
            LaunchedEffect(settingsState.brightness) {
                val activity = view.context as? ComponentActivity
                activity?.window?.let { window ->
                    val brightnessPercent = settingsState.brightness ?: 100
                    val brightnessFloat = brightnessPercent / 100f
                    window.attributes = window.attributes.also {
                        it.screenBrightness = brightnessFloat
                    }
                    Log.d("AdminMode", "Applied brightness: $brightnessPercent% ($brightnessFloat)")
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
                    },
                    onExitApp = onExitApp
            )
        }
    }
}

/**
 * Kiosk screen with admin gesture detection overlay.
 *
 * Detects 5 rapid taps in top-left corner to trigger admin mode.
 * Also applies screen brightness from DisplayState to the window.
 *
 * @param onAdminGestureDetected Called when admin gesture is completed
 */
@Composable
private fun KioskWithAdminGesture(onAdminGestureDetected: () -> Unit) {
    val kioskViewModel: KioskViewModel = hiltViewModel()
    val displayState by kioskViewModel.displayState.collectAsStateWithLifecycle()
    val gestureState: AdminGestureState = rememberAdminGestureState()

    // Apply screen brightness from DisplayState
    // screenBrightness: 0.0f = dim, 1.0f = max, -1.0f = system default
    val view = LocalView.current
    LaunchedEffect(displayState.brightness) {
        val activity = view.context as? ComponentActivity
        activity?.window?.let { window ->
            val brightnessFloat = displayState.brightness / 100f
            window.attributes = window.attributes.also {
                it.screenBrightness = brightnessFloat
            }
            Log.d("KioskScreen", "Applied brightness: ${displayState.brightness}% ($brightnessFloat)")
        }
    }

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
