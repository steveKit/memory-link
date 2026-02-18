package com.memorylink.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/** Navigation routes for Admin mode screens. */
object AdminRoutes {
    const val PIN_ENTRY = "admin/pin_entry"
    const val HOME = "admin/home"
    const val GOOGLE_SIGNIN = "admin/google_signin"
    const val CALENDAR_SELECT = "admin/calendar_select"
    const val SETTINGS = "admin/settings"
}

/**
 * Admin mode navigation graph.
 *
 * Entry point is PIN entry (or PIN setup for first-time). After PIN validation, navigates to admin
 * home.
 *
 * @param navController Navigation controller (can be passed in for integration with main nav)
 * @param viewModel Shared AdminViewModel for all admin screens
 * @param onSignInRequested Callback to launch Google Sign-In from the Activity
 * @param onExitAdmin Callback when admin mode should be exited
 * @param onExitApp Callback to exit the app entirely
 */
@Composable
fun AdminNavGraph(
        navController: NavHostController = rememberNavController(),
        viewModel: AdminViewModel = hiltViewModel(),
        onSignInRequested: () -> Unit,
        onExitAdmin: () -> Unit,
        onExitApp: () -> Unit
) {
    // Observe auto-exit due to inactivity
    val shouldExit by viewModel.shouldExitAdmin.collectAsStateWithLifecycle()

    LaunchedEffect(shouldExit) {
        if (shouldExit) {
            viewModel.clearExitFlag()
            viewModel.resetPinState()
            onExitAdmin()
        }
    }

    // Wrap NavHost in a Box that intercepts all touch events to reset the inactivity timer.
    // This ensures scrolling, dragging, and any touch resets the timer, not just button clicks.
    Box(
            modifier =
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                // Intercept at Initial pass to see events before children
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                // Reset timer on any touch (don't consume, just observe)
                                if (event.changes.any { it.pressed }) {
                                    viewModel.resetInactivityTimer()
                                }
                            }
                        }
                    }
    ) {
        NavHost(navController = navController, startDestination = AdminRoutes.PIN_ENTRY) {
        composable(AdminRoutes.PIN_ENTRY) {
            val pinState by viewModel.pinState.collectAsStateWithLifecycle()

            // Navigate to home when PIN is validated
            LaunchedEffect(pinState.isPinValid) {
                if (pinState.isPinValid) {
                    navController.navigate(AdminRoutes.HOME) {
                        popUpTo(AdminRoutes.PIN_ENTRY) { inclusive = true }
                    }
                }
            }

            PinEntryScreen(
                    pinState = pinState,
                    isSetupMode = viewModel.isPinSetupRequired,
                    onDigitClick = viewModel::addPinDigit,
                    onBackspaceClick = viewModel::removePinDigit,
                    onClearClick = viewModel::clearPin,
                    onCancel = onExitAdmin
            )
        }

        composable(AdminRoutes.HOME) {
            val authState by viewModel.authState.collectAsStateWithLifecycle()
            val calendarState by viewModel.calendarState.collectAsStateWithLifecycle()
            val syncState by viewModel.syncState.collectAsStateWithLifecycle()

            AdminHomeScreen(
                    authState = authState,
                    selectedCalendarName = calendarState.selectedCalendarName,
                    syncState = syncState,
                    lastSyncFormatted = viewModel.getLastSyncTimeFormatted(),
                    onGoogleAccountClick = { navController.navigate(AdminRoutes.GOOGLE_SIGNIN) },
                    onCalendarClick = { navController.navigate(AdminRoutes.CALENDAR_SELECT) },
                    onSettingsClick = { navController.navigate(AdminRoutes.SETTINGS) },
                    onSyncNowClick = { viewModel.triggerManualSync() },
                    onExitAdmin = {
                        viewModel.resetPinState()
                        onExitAdmin()
                    },
                    onExitApp = onExitApp
            )
        }

        composable(AdminRoutes.GOOGLE_SIGNIN) {
            val authState by viewModel.authState.collectAsStateWithLifecycle()

            GoogleSignInScreen(
                    authState = authState,
                    onSignInClick = onSignInRequested,
                    onSignOutClick = viewModel::signOut,
                    onBackClick = { navController.popBackStack() }
            )
        }

        composable(AdminRoutes.CALENDAR_SELECT) {
            val calendarState by viewModel.calendarState.collectAsStateWithLifecycle()

            // Load calendars when entering screen
            LaunchedEffect(Unit) { viewModel.loadCalendars() }

            CalendarSelectScreen(
                    calendarState = calendarState,
                    onCalendarSelected = viewModel::selectCalendar,
                    onRefresh = viewModel::loadCalendars,
                    onBackClick = { navController.popBackStack() }
            )
        }

        composable(AdminRoutes.SETTINGS) {
            val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()

            SettingsScreen(
                    settingsState = settingsState,
                    onWakeTimeChange = viewModel::setWakeTime,
                    onSleepTimeChange = viewModel::setSleepTime,
                    onWakeSolarTimeChange = viewModel::setWakeSolarTime,
                    onSleepSolarTimeChange = viewModel::setSleepSolarTime,
                    onBrightnessChange = viewModel::setBrightness,
                    onTimeFormatChange = viewModel::setUse24HourFormat,
                    onShowYearChange = viewModel::setShowYearInDate,
                    onShowEventsDuringSleepChange = viewModel::setShowEventsDuringSleep,
                    onBackClick = { navController.popBackStack() }
            )
        }
        }
    }
}
