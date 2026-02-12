package com.memorylink.ui.admin

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorylink.data.auth.GoogleAuthManager
import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.remote.GoogleCalendarService
import com.memorylink.data.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for Admin mode screens.
 *
 * Manages:
 * - PIN entry/setup state
 * - Google authentication
 * - Calendar selection
 * - Manual config overrides
 * - Inactivity timeout (5 minutes)
 *
 * Per .clinerules/20-android.md:
 * - Exit Admin: Auto-returns to kiosk after 5 minutes of inactivity
 */
@HiltViewModel
class AdminViewModel
@Inject
constructor(
        private val tokenStorage: TokenStorage,
        private val googleAuthManager: GoogleAuthManager,
        private val calendarRepository: CalendarRepository
) : ViewModel() {

    // ========== PIN State ==========

    private val _pinState = MutableStateFlow(PinState())
    val pinState: StateFlow<PinState> = _pinState.asStateFlow()

    // ========== Auth State ==========

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // ========== Calendar State ==========

    private val _calendarState = MutableStateFlow(CalendarState())
    val calendarState: StateFlow<CalendarState> = _calendarState.asStateFlow()

    // ========== Config State ==========

    private val _configState = MutableStateFlow(loadConfigState())
    val configState: StateFlow<ConfigState> = _configState.asStateFlow()

    // ========== Sync State ==========

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // ========== Inactivity Timeout ==========

    private var inactivityJob: Job? = null
    private val _shouldExitAdmin = MutableStateFlow(false)
    val shouldExitAdmin: StateFlow<Boolean> = _shouldExitAdmin.asStateFlow()

    init {
        // Load initial auth state
        updateAuthState()
        // Load initial calendar state from repository
        updateCalendarState()
        // Start inactivity timer
        resetInactivityTimer()
    }

    // ========== PIN Methods ==========

    /** Check if PIN setup is required (first-time setup). */
    val isPinSetupRequired: Boolean
        get() = !tokenStorage.isPinSet

    /** Add a digit to the current PIN entry. */
    fun addPinDigit(digit: Int) {
        resetInactivityTimer()

        val current = _pinState.value
        if (current.enteredPin.length >= 4) return
        if (current.isLockedOut) return

        val newPin = current.enteredPin + digit.toString()
        _pinState.update { it.copy(enteredPin = newPin, error = null) }

        // Auto-validate when 4 digits entered
        if (newPin.length == 4) {
            validateOrSetupPin(newPin)
        }
    }

    /** Remove the last digit from PIN entry. */
    fun removePinDigit() {
        resetInactivityTimer()

        val current = _pinState.value
        if (current.enteredPin.isEmpty()) return

        _pinState.update { it.copy(enteredPin = it.enteredPin.dropLast(1), error = null) }
    }

    /** Clear all PIN digits. */
    fun clearPin() {
        resetInactivityTimer()
        _pinState.update { it.copy(enteredPin = "", error = null) }
    }

    private fun validateOrSetupPin(pin: String) {
        viewModelScope.launch {
            if (isPinSetupRequired) {
                // First-time setup - need confirmation
                val current = _pinState.value
                if (current.confirmPin == null) {
                    // First entry - ask for confirmation
                    _pinState.update {
                        it.copy(enteredPin = "", confirmPin = pin, isConfirmingPin = true)
                    }
                } else if (current.confirmPin == pin) {
                    // Confirmation matches - save PIN
                    tokenStorage.adminPin = pin
                    _pinState.update {
                        it.copy(
                                enteredPin = "",
                                confirmPin = null,
                                isConfirmingPin = false,
                                isPinValid = true
                        )
                    }
                } else {
                    // Confirmation doesn't match - start over
                    _pinState.update {
                        it.copy(
                                enteredPin = "",
                                confirmPin = null,
                                isConfirmingPin = false,
                                error = "PINs don't match. Try again."
                        )
                    }
                }
            } else {
                // Validate existing PIN
                val isValid = tokenStorage.validatePin(pin)
                if (isValid) {
                    _pinState.update { it.copy(isPinValid = true, enteredPin = "") }
                } else {
                    val isLockedOut = tokenStorage.isPinLockedOut
                    val remainingSeconds = tokenStorage.lockoutRemainingSeconds
                    val attemptsLeft =
                            TokenStorage.MAX_PIN_ATTEMPTS - tokenStorage.failedPinAttempts

                    _pinState.update {
                        it.copy(
                                enteredPin = "",
                                error =
                                        if (isLockedOut) {
                                            "Too many attempts. Wait $remainingSeconds seconds."
                                        } else {
                                            "Incorrect PIN. $attemptsLeft attempts left."
                                        },
                                isLockedOut = isLockedOut,
                                lockoutRemainingSeconds = remainingSeconds
                        )
                    }

                    // Start lockout countdown if locked out
                    if (isLockedOut) {
                        startLockoutCountdown()
                    }
                }
            }
        }
    }

    private fun startLockoutCountdown() {
        viewModelScope.launch {
            while (tokenStorage.isPinLockedOut) {
                val remaining = tokenStorage.lockoutRemainingSeconds
                _pinState.update {
                    it.copy(
                            lockoutRemainingSeconds = remaining,
                            error = "Too many attempts. Wait $remaining seconds."
                    )
                }
                delay(1000)
            }
            _pinState.update { it.copy(isLockedOut = false, error = null) }
        }
    }

    /** Reset PIN state when exiting admin mode. */
    fun resetPinState() {
        _pinState.value = PinState()
    }

    // ========== Auth Methods ==========

    /** Get the Google Sign-In intent to launch. */
    fun getSignInIntent(): Intent = googleAuthManager.getSignInIntent()

    /** Handle the result from Google Sign-In activity. */
    suspend fun handleSignInResult(data: Intent?): GoogleAuthManager.AuthResult {
        resetInactivityTimer()
        _authState.update { it.copy(isLoading = true, error = null) }

        val result = googleAuthManager.handleSignInResult(data)

        when (result) {
            is GoogleAuthManager.AuthResult.Success -> {
                _authState.update {
                    it.copy(
                            isSignedIn = true,
                            userEmail = result.email,
                            isLoading = false,
                            error = null
                    )
                }
                // Trigger calendar sync after sign-in
                syncCalendars()
            }
            is GoogleAuthManager.AuthResult.Error -> {
                _authState.update { it.copy(isLoading = false, error = result.message) }
            }
            GoogleAuthManager.AuthResult.Cancelled -> {
                _authState.update { it.copy(isLoading = false) }
            }
            GoogleAuthManager.AuthResult.NeedsSignIn -> {
                _authState.update { it.copy(isSignedIn = false, isLoading = false) }
            }
        }

        return result
    }

    /** Sign out from Google account. */
    fun signOut() {
        resetInactivityTimer()
        viewModelScope.launch {
            googleAuthManager.signOut()
            _authState.update { AuthState() }
            _calendarState.update { CalendarState() }
        }
    }

    private fun updateAuthState() {
        _authState.update {
            it.copy(
                    isSignedIn = googleAuthManager.isSignedIn,
                    userEmail = googleAuthManager.userEmail
            )
        }
    }

    /** Load initial calendar state from repository (stored calendar selection). */
    private fun updateCalendarState() {
        _calendarState.update {
            it.copy(
                    selectedCalendarId = calendarRepository.selectedCalendarId,
                    selectedCalendarName = calendarRepository.selectedCalendarName
            )
        }
    }

    // ========== Calendar Methods ==========

    /** Fetch available calendars from Google. */
    fun loadCalendars() {
        resetInactivityTimer()
        viewModelScope.launch {
            _calendarState.update { it.copy(isLoading = true, error = null) }

            when (val result = calendarRepository.getAvailableCalendars()) {
                is GoogleCalendarService.ApiResult.Success -> {
                    _calendarState.update {
                        it.copy(
                                calendars =
                                        result.data.map { dto ->
                                            CalendarItem(
                                                    id = dto.id,
                                                    name = dto.name,
                                                    isPrimary = dto.isPrimary
                                            )
                                        },
                                selectedCalendarId = calendarRepository.selectedCalendarId,
                                selectedCalendarName = calendarRepository.selectedCalendarName,
                                isLoading = false
                        )
                    }
                }
                is GoogleCalendarService.ApiResult.Error -> {
                    _calendarState.update { it.copy(isLoading = false, error = result.message) }
                }
                GoogleCalendarService.ApiResult.NotAuthenticated -> {
                    _calendarState.update {
                        it.copy(isLoading = false, error = "Please sign in first")
                    }
                }
                GoogleCalendarService.ApiResult.SyncTokenExpired -> {
                    // Should not happen when fetching calendar list, but handle anyway
                    _calendarState.update { it.copy(isLoading = false, error = "Sync token error") }
                }
            }
        }
    }

    /** Select a calendar for event syncing. */
    fun selectCalendar(calendarId: String, calendarName: String) {
        resetInactivityTimer()
        viewModelScope.launch {
            // selectCalendar clears cache if calendar changed
            calendarRepository.selectCalendar(calendarId, calendarName)
            _calendarState.update {
                it.copy(selectedCalendarId = calendarId, selectedCalendarName = calendarName)
            }

            // Trigger sync after selecting calendar
            // syncEvents() will do a full sync since syncToken was cleared on calendar change
            syncCalendars()
        }
    }

    private fun syncCalendars() {
        viewModelScope.launch { calendarRepository.syncEvents() }
    }

    /**
     * Manually trigger a calendar sync. Updates sync state to show progress/result. Called from
     * Admin UI "Sync Now" button.
     */
    fun triggerManualSync() {
        resetInactivityTimer()
        viewModelScope.launch {
            _syncState.update { it.copy(isSyncing = true, lastResult = null) }

            val result = calendarRepository.syncEvents()
            val resultMessage =
                    when (result) {
                        is CalendarRepository.SyncResult.Success ->
                                "Synced ${result.eventCount} events" +
                                        if (result.deletedCount > 0)
                                                ", ${result.deletedCount} deleted"
                                        else ""
                        is CalendarRepository.SyncResult.Error -> "Error: ${result.message}"
                        CalendarRepository.SyncResult.NotAuthenticated -> "Not signed in"
                        CalendarRepository.SyncResult.NoCalendarSelected -> "No calendar selected"
                    }

            _syncState.update {
                it.copy(
                        isSyncing = false,
                        lastResult = resultMessage,
                        lastSyncTime = tokenStorage.lastSyncTime
                )
            }
        }
    }

    /** Get the last sync time in a human-readable format. */
    fun getLastSyncTimeFormatted(): String {
        val lastSync = tokenStorage.lastSyncTime
        if (lastSync == 0L) return "Never"

        val elapsed = System.currentTimeMillis() - lastSync
        val seconds = elapsed / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours hours ago"
            else -> "${hours / 24} days ago"
        }
    }

    // ========== Config Methods ==========

    private fun loadConfigState(): ConfigState {
        val wakeTimeStr = tokenStorage.manualWakeTime
        val sleepTimeStr = tokenStorage.manualSleepTime

        return ConfigState(
                wakeTime = wakeTimeStr?.let { parseTime(it) },
                sleepTime = sleepTimeStr?.let { parseTime(it) },
                brightness = tokenStorage.manualBrightness.takeIf { it >= 0 },
                use24HourFormat = tokenStorage.manualUse24HourFormat
        )
    }

    /** Update wake time override. */
    fun setWakeTime(time: LocalTime?) {
        resetInactivityTimer()
        tokenStorage.manualWakeTime = time?.toString()
        _configState.update { it.copy(wakeTime = time) }
    }

    /** Update sleep time override. */
    fun setSleepTime(time: LocalTime?) {
        resetInactivityTimer()
        tokenStorage.manualSleepTime = time?.toString()
        _configState.update { it.copy(sleepTime = time) }
    }

    /** Update brightness override. */
    fun setBrightness(brightness: Int?) {
        resetInactivityTimer()
        tokenStorage.manualBrightness = brightness ?: -1
        _configState.update { it.copy(brightness = brightness) }
    }

    /** Update time format override. */
    fun setUse24HourFormat(use24Hour: Boolean?) {
        resetInactivityTimer()
        tokenStorage.manualUse24HourFormat = use24Hour
        _configState.update { it.copy(use24HourFormat = use24Hour) }
    }

    /** Clear all manual overrides. */
    fun clearAllOverrides() {
        resetInactivityTimer()
        tokenStorage.clearManualOverrides()
        _configState.value = ConfigState()
    }

    private fun parseTime(timeStr: String): LocalTime? {
        return try {
            LocalTime.parse(timeStr)
        } catch (e: Exception) {
            null
        }
    }

    // ========== Inactivity Timeout ==========

    /** Reset the inactivity timer. Call on any user interaction. */
    fun resetInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob =
                viewModelScope.launch {
                    delay(INACTIVITY_TIMEOUT_MS)
                    _shouldExitAdmin.value = true
                }
    }

    /** Clear the exit flag when navigating back to kiosk. */
    fun clearExitFlag() {
        _shouldExitAdmin.value = false
    }

    override fun onCleared() {
        super.onCleared()
        inactivityJob?.cancel()
    }

    companion object {
        /** 5 minutes inactivity timeout per .clinerules/20-android.md */
        const val INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L
    }
}

// ========== State Classes ==========

data class PinState(
        val enteredPin: String = "",
        val confirmPin: String? = null,
        val isConfirmingPin: Boolean = false,
        val isPinValid: Boolean = false,
        val error: String? = null,
        val isLockedOut: Boolean = false,
        val lockoutRemainingSeconds: Int = 0
)

data class AuthState(
        val isSignedIn: Boolean = false,
        val userEmail: String? = null,
        val isLoading: Boolean = false,
        val error: String? = null
)

data class CalendarState(
        val calendars: List<CalendarItem> = emptyList(),
        val selectedCalendarId: String? = null,
        val selectedCalendarName: String? = null,
        val isLoading: Boolean = false,
        val error: String? = null
)

data class CalendarItem(val id: String, val name: String, val isPrimary: Boolean)

data class ConfigState(
        val wakeTime: LocalTime? = null,
        val sleepTime: LocalTime? = null,
        val brightness: Int? = null,
        val use24HourFormat: Boolean? = null
)

data class SyncState(
        val isSyncing: Boolean = false,
        val lastResult: String? = null,
        val lastSyncTime: Long = 0L
)
