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

        // Observe settings from SettingsRepository for resolved times
        viewModelScope.launch {
            settingsRepository.settings.collect { appSettings ->
                _settingsState.update {
                    it.copy(
                            resolvedWakeTime = appSettings.wakeTime,
                            resolvedSleepTime = appSettings.sleepTime
                    )
                }
            }
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

    // ========== Settings Methods ==========

    private fun loadSettingsState(): SettingsState {
        val wakeTimeStr = tokenStorage.wakeTime
        val sleepTimeStr = tokenStorage.sleepTime

        return SettingsState(
                wakeTime = wakeTimeStr?.let { parseTime(it) },
                sleepTime = sleepTimeStr?.let { parseTime(it) },
                brightness = tokenStorage.brightness.takeIf { it >= 0 },
                use24HourFormat = tokenStorage.use24HourFormat,
                showYearInDate = tokenStorage.showYear,
                wakeSolarRef = tokenStorage.wakeSolarRef,
                wakeSolarOffset = tokenStorage.wakeSolarOffset,
                sleepSolarRef = tokenStorage.sleepSolarRef,
                sleepSolarOffset = tokenStorage.sleepSolarOffset
        )
    }

    /** Update wake time (static time). Clears any solar reference. */
    fun setWakeTime(time: LocalTime?) {
        resetInactivityTimer()
        tokenStorage.setStaticWakeTime(time?.toString())
        _settingsState.update { it.copy(wakeTime = time, wakeSolarRef = null, wakeSolarOffset = 0) }
        notifySettingsChanged()
    }

    /** Update sleep time (static time). Clears any solar reference. */
    fun setSleepTime(time: LocalTime?) {
        resetInactivityTimer()
        tokenStorage.setStaticSleepTime(time?.toString())
        _settingsState.update {
            it.copy(sleepTime = time, sleepSolarRef = null, sleepSolarOffset = 0)
        }
        notifySettingsChanged()
    }

    /** Update wake time to solar-based. Clears static time. */
    fun setWakeSolarTime(solarRef: String, offsetMinutes: Int) {
        resetInactivityTimer()
        tokenStorage.setSolarWakeTime(solarRef, offsetMinutes)
        _settingsState.update {
            it.copy(wakeTime = null, wakeSolarRef = solarRef, wakeSolarOffset = offsetMinutes)
        }
        notifySettingsChanged()
    }

    /** Update sleep time to solar-based. Clears static time. */
    fun setSleepSolarTime(solarRef: String, offsetMinutes: Int) {
        resetInactivityTimer()
        tokenStorage.setSolarSleepTime(solarRef, offsetMinutes)
        _settingsState.update {
            it.copy(sleepTime = null, sleepSolarRef = solarRef, sleepSolarOffset = offsetMinutes)
        }
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

/**
 * Unified settings state for admin panel. Shows current values that can be edited. Last write wins.
 */
data class SettingsState(
        // Static time settings (null if using solar time)
        val wakeTime: LocalTime? = null,
        val sleepTime: LocalTime? = null,
        // Solar time settings (null if using static time)
        val wakeSolarRef: String? = null,
        val wakeSolarOffset: Int = 0,
        val sleepSolarRef: String? = null,
        val sleepSolarOffset: Int = 0,
        // Other settings (null = use default)
        val brightness: Int? = null,
        val use24HourFormat: Boolean? = null,
        val showYearInDate: Boolean? = null,
        // Resolved times (after solar calculation, for display)
        val resolvedWakeTime: LocalTime = LocalTime.of(6, 0),
        val resolvedSleepTime: LocalTime = LocalTime.of(21, 0)
)

/**
 * Represents a solar time configuration option.
 *
 * @param solarRef "SUNRISE" or "SUNSET"
 * @param offsetMinutes Offset in minutes (can be negative)
 */
data class SolarTimeOption(val solarRef: String, val offsetMinutes: Int) {
    /** Display label for the option (e.g., "Sunrise", "Sunset + 30 min"). */
    val displayLabel: String
        get() {
            val base = solarRef.lowercase().replaceFirstChar { it.uppercase() }
            return when {
                offsetMinutes == 0 -> base
                offsetMinutes > 0 -> "$base + $offsetMinutes min"
                else -> "$base - ${-offsetMinutes} min"
            }
        }

    companion object {
        /** Available wake time options (sunrise-focused). */
        val WAKE_OPTIONS =
                listOf(
                        SolarTimeOption("SUNRISE", -60),
                        SolarTimeOption("SUNRISE", -45),
                        SolarTimeOption("SUNRISE", -30),
                        SolarTimeOption("SUNRISE", -15),
                        SolarTimeOption("SUNRISE", 0),
                        SolarTimeOption("SUNRISE", 15),
                        SolarTimeOption("SUNRISE", 30),
                        SolarTimeOption("SUNRISE", 45),
                        SolarTimeOption("SUNRISE", 60)
                )

        /** Available sleep time options (sunset-focused). */
        val SLEEP_OPTIONS =
                listOf(
                        SolarTimeOption("SUNSET", -60),
                        SolarTimeOption("SUNSET", -45),
                        SolarTimeOption("SUNSET", -30),
                        SolarTimeOption("SUNSET", -15),
                        SolarTimeOption("SUNSET", 0),
                        SolarTimeOption("SUNSET", 15),
                        SolarTimeOption("SUNSET", 30),
                        SolarTimeOption("SUNSET", 45),
                        SolarTimeOption("SUNSET", 60)
                )
    }
}

data class SyncState(
        val isSyncing: Boolean = false,
        val lastResult: String? = null,
        val lastSyncTime: Long = 0L
)
