package com.memorylink.ui.setup

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorylink.data.auth.GoogleAuthManager
import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.remote.GoogleCalendarService
import com.memorylink.data.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Setup Wizard flow.
 *
 * Manages the 3-step first-time setup:
 * 1. Create PIN
 * 2. Sign in to Google
 * 3. Select calendar
 *
 * Unlike AdminViewModel, this has no inactivity timeout since users need time during setup.
 */
@HiltViewModel
class SetupWizardViewModel
@Inject
constructor(
        private val tokenStorage: TokenStorage,
        private val googleAuthManager: GoogleAuthManager,
        private val calendarRepository: CalendarRepository
) : ViewModel() {

    // ========== Wizard State ==========

    private val _wizardState = MutableStateFlow(WizardState())
    val wizardState: StateFlow<WizardState> = _wizardState.asStateFlow()

    // ========== PIN State ==========

    private val _pinState = MutableStateFlow(SetupPinState())
    val pinState: StateFlow<SetupPinState> = _pinState.asStateFlow()

    // ========== Auth State ==========

    private val _authState = MutableStateFlow(SetupAuthState())
    val authState: StateFlow<SetupAuthState> = _authState.asStateFlow()

    // ========== Calendar State ==========

    private val _calendarState = MutableStateFlow(SetupCalendarState())
    val calendarState: StateFlow<SetupCalendarState> = _calendarState.asStateFlow()

    init {
        // Check if any steps are already complete (e.g., partial setup)
        initializeState()
    }

    private fun initializeState() {
        // Check PIN state
        if (tokenStorage.isPinSet) {
            _pinState.update { it.copy(isPinCreated = true) }
        }

        // Check auth state
        if (tokenStorage.isSignedIn) {
            _authState.update {
                it.copy(isSignedIn = true, userEmail = tokenStorage.userEmail)
            }
        }

        // Check calendar state
        tokenStorage.selectedCalendarId?.let { calendarId ->
            _calendarState.update { it.copy(selectedCalendarId = calendarId) }
        }
    }

    // ========== PIN Methods ==========

    /** Add a digit to the current PIN entry. */
    fun addPinDigit(digit: Int) {
        val current = _pinState.value
        if (current.enteredPin.length >= 4) return

        val newPin = current.enteredPin + digit.toString()
        _pinState.update { it.copy(enteredPin = newPin, error = null) }

        // Auto-process when 4 digits entered
        if (newPin.length == 4) {
            processPinEntry(newPin)
        }
    }

    /** Remove the last digit from PIN entry. */
    fun removePinDigit() {
        val current = _pinState.value
        if (current.enteredPin.isEmpty()) return

        _pinState.update { it.copy(enteredPin = it.enteredPin.dropLast(1), error = null) }
    }

    /** Clear all PIN digits. */
    fun clearPin() {
        _pinState.update { it.copy(enteredPin = "", error = null) }
    }

    private fun processPinEntry(pin: String) {
        val current = _pinState.value
        if (current.confirmPin == null) {
            // First entry - ask for confirmation
            _pinState.update { it.copy(enteredPin = "", confirmPin = pin, isConfirmingPin = true) }
        } else if (current.confirmPin == pin) {
            // Confirmation matches - save PIN
            tokenStorage.adminPin = pin
            _pinState.update {
                it.copy(
                        enteredPin = "",
                        confirmPin = null,
                        isConfirmingPin = false,
                        isPinCreated = true
                )
            }
            // Auto-advance to next step
            advanceToNextStep()
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
    }

    // ========== Auth Methods ==========

    /** Get the Google Sign-In intent to launch. */
    fun getSignInIntent(): Intent = googleAuthManager.getSignInIntent()

    /** Handle the result from Google Sign-In activity. */
    suspend fun handleSignInResult(data: Intent?): GoogleAuthManager.AuthResult {
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
                // Auto-advance to calendar selection and load calendars
                advanceToNextStep()
                loadCalendars()
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

    // ========== Calendar Methods ==========

    /** Fetch available calendars from Google. */
    fun loadCalendars() {
        viewModelScope.launch {
            _calendarState.update { it.copy(isLoading = true, error = null) }

            when (val result = calendarRepository.getAvailableCalendars()) {
                is GoogleCalendarService.ApiResult.Success -> {
                    _calendarState.update {
                        it.copy(
                                calendars =
                                        result.data.map { dto ->
                                            SetupCalendarItem(
                                                    id = dto.id,
                                                    name = dto.name,
                                                    isPrimary = dto.isPrimary
                                            )
                                        },
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
            }
        }
    }

    /** Select a calendar for event syncing. */
    fun selectCalendar(calendarId: String) {
        calendarRepository.selectCalendar(calendarId)
        _calendarState.update { it.copy(selectedCalendarId = calendarId) }

        // Trigger initial sync
        viewModelScope.launch { calendarRepository.syncEvents(forceFullSync = true) }

        // Mark setup complete and trigger completion
        _wizardState.update { it.copy(isSetupComplete = true) }
    }

    // ========== Navigation ==========

    private fun advanceToNextStep() {
        val currentStep = _wizardState.value.currentStep
        if (currentStep < 3) {
            _wizardState.update { it.copy(currentStep = currentStep + 1) }
        }
    }

    /** Go back to the previous step (only for Google Sign-In step). */
    fun goToPreviousStep() {
        val currentStep = _wizardState.value.currentStep
        if (currentStep > 1) {
            _wizardState.update { it.copy(currentStep = currentStep - 1) }
        }
    }

    /** Check if back navigation is allowed from current step. */
    val canGoBack: Boolean
        get() = _wizardState.value.currentStep > 1
}

// ========== State Classes ==========

data class WizardState(
        val currentStep: Int = 1, // 1 = PIN, 2 = Google, 3 = Calendar
        val totalSteps: Int = 3,
        val isSetupComplete: Boolean = false
)

data class SetupPinState(
        val enteredPin: String = "",
        val confirmPin: String? = null,
        val isConfirmingPin: Boolean = false,
        val isPinCreated: Boolean = false,
        val error: String? = null
)

data class SetupAuthState(
        val isSignedIn: Boolean = false,
        val userEmail: String? = null,
        val isLoading: Boolean = false,
        val error: String? = null
)

data class SetupCalendarState(
        val calendars: List<SetupCalendarItem> = emptyList(),
        val selectedCalendarId: String? = null,
        val isLoading: Boolean = false,
        val error: String? = null
)

data class SetupCalendarItem(val id: String, val name: String, val isPrimary: Boolean)
