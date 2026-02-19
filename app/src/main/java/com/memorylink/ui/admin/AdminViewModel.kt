package com.memorylink.ui.admin

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorylink.data.auth.GoogleAuthManager
import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.remote.GoogleCalendarService
import com.memorylink.data.repository.CalendarRepository
import com.memorylink.data.repository.SettingsRepository
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
 * - Display settings
 * - Inactivity timeout (30 seconds)
 *
 * Per .clinerules/20-android.md:
 * - Exit Admin: Auto-returns to kiosk after 30 seconds of inactivity
 */
@HiltViewModel
class AdminViewModel
@Inject
constructor(
        private val tokenStorage: TokenStorage,
        private val googleAuthManager: GoogleAuthManager,
        private val calendarRepository: CalendarRepository,
        private val settingsRepository: SettingsRepository
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

    // ========== Settings State ==========

    private val _settingsState = MutableStateFlow(loadSettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    // ========== Sync State ==========

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private var syncCooldownJob: Job? = null

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
        // Restore sync cooldown state from persisted timestamp
        restoreSyncCooldownState()

        // Observe settings from SettingsRepository for resolved times
        viewModelScope.launch {
            settingsRepository.settings.collect { appSettings ->
                _settingsState.update {
                    it.copy(
                            wakeTime = appSettings.wakeTime,
                            sleepTime = appSettings.sleepTime
                    )
                }
            }
        }
    }

    /**
     * Restore sync cooldown state from persisted timestamp.
     * Called on ViewModel init to maintain cooldown across admin panel exits/re-entries.
     */
    private fun restoreSyncCooldownState() {
        val lastManualSync = tokenStorage.lastManualSyncTime
        if (lastManualSync == 0L) return

        val elapsedSeconds = (System.currentTimeMillis() - lastManualSync) / 1000
        val remainingSeconds = SYNC_COOLDOWN_SECONDS - elapsedSeconds.toInt()

        if (remainingSeconds > 0) {
            _syncState.update { it.copy(cooldownSecondsRemaining = remainingSeconds) }
            startSyncCooldown(remainingSeconds)
        }
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
        // Delay briefly to allow the 4th dot to render before validation clears the PIN
        if (newPin.length == 4) {
            viewModelScope.launch {
                delay(PIN_VALIDATION_DELAY_MS)
                validateOrSetupPin(newPin)
            }
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
                    selectedCalendarName = calendarRepository.selectedCalendarName,
                    holidayCalendarId = calendarRepository.holidayCalendarId,
                    holidayCalendarName = calendarRepository.holidayCalendarName
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
            syncCalendars()
        }
    }

    private fun syncCalendars() {
        viewModelScope.launch { calendarRepository.syncEvents() }
    }

    // ========== Holiday Calendar Methods ==========

    /** Select a holiday calendar. */
    fun selectHolidayCalendar(calendarId: String, calendarName: String) {
        resetInactivityTimer()
        viewModelScope.launch {
            calendarRepository.selectHolidayCalendar(calendarId, calendarName)
            _calendarState.update {
                it.copy(holidayCalendarId = calendarId, holidayCalendarName = calendarName)
            }
            // Trigger holiday sync
            calendarRepository.syncHolidayEvents(force = true)
        }
    }

    /** Clear the holiday calendar selection. */
    fun clearHolidayCalendar() {
        resetInactivityTimer()
        viewModelScope.launch {
            calendarRepository.clearHolidayCalendar()
            _calendarState.update { it.copy(holidayCalendarId = null, holidayCalendarName = null) }
        }
    }

    /** Update show holidays setting. */
    fun setShowHolidays(show: Boolean) {
        resetInactivityTimer()
        tokenStorage.showHolidays = show
        _settingsState.update { it.copy(showHolidays = show) }
        notifySettingsChanged()
    }

    /**
     * Manually trigger a calendar sync. Updates sync state to show progress/result. Called from
     * Admin UI "Sync Now" button.
     *
     * Includes a 30-second cooldown to prevent API abuse.
     */
    fun triggerManualSync() {
        resetInactivityTimer()

        // Check if cooldown is active
        if (_syncState.value.cooldownSecondsRemaining > 0) {
            return
        }

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

            // Persist the manual sync timestamp for cooldown across admin exits
            tokenStorage.lastManualSyncTime = System.currentTimeMillis()

            _syncState.update {
                it.copy(
                        isSyncing = false,
                        lastResult = resultMessage,
                        lastSyncTime = tokenStorage.lastSyncTime,
                        cooldownSecondsRemaining = SYNC_COOLDOWN_SECONDS
                )
            }

            // Start cooldown countdown
            startSyncCooldown(SYNC_COOLDOWN_SECONDS)
        }
    }

    /**
     * Start the cooldown countdown timer.
     * @param initialSeconds Starting seconds for countdown (allows restoring mid-cooldown)
     */
    private fun startSyncCooldown(initialSeconds: Int = SYNC_COOLDOWN_SECONDS) {
        syncCooldownJob?.cancel()
        syncCooldownJob =
                viewModelScope.launch {
                    var remaining = initialSeconds
                    while (remaining > 0) {
                        delay(1000)
                        remaining--
                        _syncState.update { it.copy(cooldownSecondsRemaining = remaining) }
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

    // ========== Settings Methods ==========

    private fun loadSettingsState(): SettingsState {
        val wakeTimeStr = tokenStorage.wakeTime
        val sleepTimeStr = tokenStorage.sleepTime

        return SettingsState(
                wakeTime = wakeTimeStr?.let { parseTime(it) } ?: LocalTime.of(6, 0),
                sleepTime = sleepTimeStr?.let { parseTime(it) } ?: LocalTime.of(21, 30),
                brightness = tokenStorage.brightness.takeIf { it >= 0 },
                use24HourFormat = tokenStorage.use24HourFormat,
                showYearInDate = tokenStorage.showYear,
                showEventsDuringSleep = tokenStorage.showEventsDuringSleep,
                showHolidays = tokenStorage.showHolidays
        )
    }

    /** Update wake time. */
    fun setWakeTime(time: LocalTime?) {
        resetInactivityTimer()
        tokenStorage.wakeTime = time?.toString()
        _settingsState.update { it.copy(wakeTime = time ?: LocalTime.of(6, 0)) }
        notifySettingsChanged()
    }

    /** Update sleep time. */
    fun setSleepTime(time: LocalTime?) {
        resetInactivityTimer()
        tokenStorage.sleepTime = time?.toString()
        _settingsState.update { it.copy(sleepTime = time ?: LocalTime.of(21, 30)) }
        notifySettingsChanged()
    }

    /** Update brightness. */
    fun setBrightness(brightness: Int?) {
        resetInactivityTimer()
        tokenStorage.brightness = brightness ?: -1
        _settingsState.update { it.copy(brightness = brightness) }
        notifySettingsChanged()
    }

    /** Update time format. */
    fun setUse24HourFormat(use24Hour: Boolean?) {
        resetInactivityTimer()
        tokenStorage.use24HourFormat = use24Hour
        _settingsState.update { it.copy(use24HourFormat = use24Hour) }
        notifySettingsChanged()
    }

    /** Update show year in date. */
    fun setShowYearInDate(showYear: Boolean?) {
        resetInactivityTimer()
        tokenStorage.showYear = showYear
        _settingsState.update { it.copy(showYearInDate = showYear) }
        notifySettingsChanged()
    }

    /** Update show events during sleep. */
    fun setShowEventsDuringSleep(showEvents: Boolean?) {
        resetInactivityTimer()
        tokenStorage.showEventsDuringSleep = showEvents
        _settingsState.update { it.copy(showEventsDuringSleep = showEvents) }
        notifySettingsChanged()
    }

    /** Notify SettingsRepository that settings have changed. */
    private fun notifySettingsChanged() {
        viewModelScope.launch { settingsRepository.onSettingsChanged() }
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
        /** 30 seconds inactivity timeout */
        const val INACTIVITY_TIMEOUT_MS = 30 * 1000L

        /** Delay before validating PIN to allow 4th dot to render */
        const val PIN_VALIDATION_DELAY_MS = 150L

        /** Cooldown duration after manual sync (prevents API abuse) */
        const val SYNC_COOLDOWN_SECONDS = 30
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
        /** Optional holiday calendar ID. */
        val holidayCalendarId: String? = null,
        /** Optional holiday calendar name for display. */
        val holidayCalendarName: String? = null,
        val isLoading: Boolean = false,
        val error: String? = null
)

data class CalendarItem(val id: String, val name: String, val isPrimary: Boolean)

/**
 * Unified settings state for admin panel.
 * Shows current values that can be edited. Last write wins.
 */
data class SettingsState(
        val wakeTime: LocalTime = LocalTime.of(6, 0),
        val sleepTime: LocalTime = LocalTime.of(21, 30),
        val brightness: Int? = null,
        val use24HourFormat: Boolean? = null,
        val showYearInDate: Boolean? = null,
        val showEventsDuringSleep: Boolean? = null,
        /**
         * Whether to show holiday events on the display.
         * Only applicable if a holiday calendar is configured.
         * Default: true
         */
        val showHolidays: Boolean = true
)

data class SyncState(
        val isSyncing: Boolean = false,
        val lastResult: String? = null,
        val lastSyncTime: Long = 0L,
        /** Remaining cooldown seconds before manual sync is allowed again (0 = no cooldown) */
        val cooldownSecondsRemaining: Int = 0
)
