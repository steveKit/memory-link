package com.memorylink.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for OAuth tokens using EncryptedSharedPreferences.
 *
 * Per .clinerules/20-android.md:
 * - Config Storage: EncryptedSharedPreferences for PIN, OAuth tokens, settings.
 * - Token encryption: AES-256-GCM via AndroidX Security library.
 *
 * Stores:
 * - Access token (short-lived, ~1 hour)
 * - Refresh token (long-lived, used for offline access)
 * - Token expiration timestamp
 * - Selected calendar ID
 * - User email (for display purposes)
 */
@Singleton
class TokenStorage @Inject constructor(@ApplicationContext private val context: Context) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Store OAuth access token. */
    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    /** Store OAuth refresh token for offline access. */
    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    /** Token expiration timestamp in epoch millis. */
    var tokenExpirationTime: Long
        get() = prefs.getLong(KEY_TOKEN_EXPIRATION, 0L)
        set(value) = prefs.edit().putLong(KEY_TOKEN_EXPIRATION, value).apply()

    /** Selected calendar ID for event fetching. */
    var selectedCalendarId: String?
        get() = prefs.getString(KEY_SELECTED_CALENDAR_ID, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_CALENDAR_ID, value).apply()

    /** User's email address (for display purposes). */
    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    /** Last successful sync timestamp in epoch millis. */
    var lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIME, value).apply()

    // ========== Admin PIN Management ==========

    /** Store the admin 4-digit PIN (encrypted). Only set during first-time setup or PIN change. */
    var adminPin: String?
        get() = prefs.getString(KEY_ADMIN_PIN, null)
        set(value) = prefs.edit().putString(KEY_ADMIN_PIN, value).apply()

    /** Check if admin PIN has been configured. */
    val isPinSet: Boolean
        get() = !adminPin.isNullOrBlank()

    /** Track failed PIN entry attempts. Reset after successful entry. */
    var failedPinAttempts: Int
        get() = prefs.getInt(KEY_FAILED_PIN_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(KEY_FAILED_PIN_ATTEMPTS, value).apply()

    /** Lockout end time after 3 failed attempts. Epoch millis. 0 means no active lockout. */
    var pinLockoutEndTime: Long
        get() = prefs.getLong(KEY_PIN_LOCKOUT_END, 0L)
        set(value) = prefs.edit().putLong(KEY_PIN_LOCKOUT_END, value).apply()

    /** Check if currently locked out due to failed PIN attempts. */
    val isPinLockedOut: Boolean
        get() = pinLockoutEndTime > System.currentTimeMillis()

    /** Get remaining lockout time in seconds, or 0 if not locked out. */
    val lockoutRemainingSeconds: Int
        get() {
            val remaining = pinLockoutEndTime - System.currentTimeMillis()
            return if (remaining > 0) (remaining / 1000).toInt() else 0
        }

    /**
     * Validate entered PIN against stored PIN. Manages failed attempts and lockout automatically.
     *
     * @param enteredPin The 4-digit PIN entered by user
     * @return true if PIN matches, false otherwise
     */
    fun validatePin(enteredPin: String): Boolean {
        // Check for active lockout
        if (isPinLockedOut) {
            return false
        }

        val storedPin = adminPin ?: return false

        return if (enteredPin == storedPin) {
            // Success - reset failed attempts
            failedPinAttempts = 0
            pinLockoutEndTime = 0L
            true
        } else {
            // Failed - increment attempts
            val attempts = failedPinAttempts + 1
            failedPinAttempts = attempts

            // Lockout after 3 failed attempts (30 seconds)
            if (attempts >= MAX_PIN_ATTEMPTS) {
                pinLockoutEndTime = System.currentTimeMillis() + LOCKOUT_DURATION_MS
                failedPinAttempts = 0 // Reset for next lockout cycle
            }
            false
        }
    }

    /** Reset PIN lockout state. Called after lockout period expires. */
    fun resetPinLockout() {
        failedPinAttempts = 0
        pinLockoutEndTime = 0L
    }

    // ========== Manual Config Overrides ==========

    /** Manually overridden wake time (HH:mm format), or null to use [CONFIG] events. */
    var manualWakeTime: String?
        get() = prefs.getString(KEY_MANUAL_WAKE_TIME, null)
        set(value) = prefs.edit().putString(KEY_MANUAL_WAKE_TIME, value).apply()

    /** Manually overridden sleep time (HH:mm format), or null to use [CONFIG] events. */
    var manualSleepTime: String?
        get() = prefs.getString(KEY_MANUAL_SLEEP_TIME, null)
        set(value) = prefs.edit().putString(KEY_MANUAL_SLEEP_TIME, value).apply()

    /** Manually overridden brightness (0-100), or -1 to use [CONFIG] events. */
    var manualBrightness: Int
        get() = prefs.getInt(KEY_MANUAL_BRIGHTNESS, -1)
        set(value) = prefs.edit().putInt(KEY_MANUAL_BRIGHTNESS, value).apply()

    /** Manually overridden time format. null = use [CONFIG], true = 24h, false = 12h. */
    var manualUse24HourFormat: Boolean?
        get() {
            return if (prefs.contains(KEY_MANUAL_TIME_FORMAT)) {
                prefs.getBoolean(KEY_MANUAL_TIME_FORMAT, false)
            } else {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_MANUAL_TIME_FORMAT).apply()
            } else {
                prefs.edit().putBoolean(KEY_MANUAL_TIME_FORMAT, value).apply()
            }
        }

    /** Manually overridden font size (sp), or -1 to use [CONFIG] events. */
    var manualFontSize: Int
        get() = prefs.getInt(KEY_MANUAL_FONT_SIZE, -1)
        set(value) = prefs.edit().putInt(KEY_MANUAL_FONT_SIZE, value).apply()

    /** Manually overridden message area percent, or -1 to use [CONFIG] events. */
    var manualMessageSize: Int
        get() = prefs.getInt(KEY_MANUAL_MESSAGE_SIZE, -1)
        set(value) = prefs.edit().putInt(KEY_MANUAL_MESSAGE_SIZE, value).apply()

    /** Clear all manual config overrides, reverting to [CONFIG] event values. */
    fun clearManualOverrides() {
        prefs.edit()
                .remove(KEY_MANUAL_WAKE_TIME)
                .remove(KEY_MANUAL_SLEEP_TIME)
                .remove(KEY_MANUAL_BRIGHTNESS)
                .remove(KEY_MANUAL_TIME_FORMAT)
                .remove(KEY_MANUAL_FONT_SIZE)
                .remove(KEY_MANUAL_MESSAGE_SIZE)
                .apply()
    }

    // ========== [CONFIG] Event Settings ==========
    // These are set when parsing [CONFIG] calendar events.
    // Priority: Manual Override > Config Event > Default

    /** Config event wake time (HH:mm format), or null if not set. */
    var configWakeTime: String?
        get() = prefs.getString(KEY_CONFIG_WAKE_TIME, null)
        set(value) = prefs.edit().putString(KEY_CONFIG_WAKE_TIME, value).apply()

    /** Config event sleep time (HH:mm format), or null if not set. */
    var configSleepTime: String?
        get() = prefs.getString(KEY_CONFIG_SLEEP_TIME, null)
        set(value) = prefs.edit().putString(KEY_CONFIG_SLEEP_TIME, value).apply()

    /** Config event brightness (0-100), or -1 if not set. */
    var configBrightness: Int
        get() = prefs.getInt(KEY_CONFIG_BRIGHTNESS, -1)
        set(value) = prefs.edit().putInt(KEY_CONFIG_BRIGHTNESS, value).apply()

    /** Config event time format. null = not set, true = 24h, false = 12h. */
    var configUse24HourFormat: Boolean?
        get() {
            return if (prefs.contains(KEY_CONFIG_TIME_FORMAT)) {
                prefs.getBoolean(KEY_CONFIG_TIME_FORMAT, false)
            } else {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_CONFIG_TIME_FORMAT).apply()
            } else {
                prefs.edit().putBoolean(KEY_CONFIG_TIME_FORMAT, value).apply()
            }
        }

    /** Config event font size (sp), or -1 if not set. */
    var configFontSize: Int
        get() = prefs.getInt(KEY_CONFIG_FONT_SIZE, -1)
        set(value) = prefs.edit().putInt(KEY_CONFIG_FONT_SIZE, value).apply()

    /** Config event message area percent, or -1 if not set. */
    var configMessageSize: Int
        get() = prefs.getInt(KEY_CONFIG_MESSAGE_SIZE, -1)
        set(value) = prefs.edit().putInt(KEY_CONFIG_MESSAGE_SIZE, value).apply()

    // ========== Solar Time Config (SUNRISE/SUNSET) ==========

    /** Wake solar reference ("SUNRISE" or "SUNSET"), or null for static time. */
    var configWakeSolarRef: String?
        get() = prefs.getString(KEY_CONFIG_WAKE_SOLAR_REF, null)
        set(value) = prefs.edit().putString(KEY_CONFIG_WAKE_SOLAR_REF, value).apply()

    /** Wake solar offset in minutes (e.g., +30 or -15). */
    var configWakeSolarOffset: Int
        get() = prefs.getInt(KEY_CONFIG_WAKE_SOLAR_OFFSET, 0)
        set(value) = prefs.edit().putInt(KEY_CONFIG_WAKE_SOLAR_OFFSET, value).apply()

    /** Sleep solar reference ("SUNRISE" or "SUNSET"), or null for static time. */
    var configSleepSolarRef: String?
        get() = prefs.getString(KEY_CONFIG_SLEEP_SOLAR_REF, null)
        set(value) = prefs.edit().putString(KEY_CONFIG_SLEEP_SOLAR_REF, value).apply()

    /** Sleep solar offset in minutes (e.g., +30 or -15). */
    var configSleepSolarOffset: Int
        get() = prefs.getInt(KEY_CONFIG_SLEEP_SOLAR_OFFSET, 0)
        set(value) = prefs.edit().putInt(KEY_CONFIG_SLEEP_SOLAR_OFFSET, value).apply()

    /** Clear all config event settings (used when resetting or testing). */
    fun clearConfigSettings() {
        prefs.edit()
                .remove(KEY_CONFIG_WAKE_TIME)
                .remove(KEY_CONFIG_SLEEP_TIME)
                .remove(KEY_CONFIG_BRIGHTNESS)
                .remove(KEY_CONFIG_TIME_FORMAT)
                .remove(KEY_CONFIG_FONT_SIZE)
                .remove(KEY_CONFIG_MESSAGE_SIZE)
                .remove(KEY_CONFIG_WAKE_SOLAR_REF)
                .remove(KEY_CONFIG_WAKE_SOLAR_OFFSET)
                .remove(KEY_CONFIG_SLEEP_SOLAR_REF)
                .remove(KEY_CONFIG_SLEEP_SOLAR_OFFSET)
                .apply()
    }

    /** Check if user is signed in (has refresh token). */
    val isSignedIn: Boolean
        get() = !refreshToken.isNullOrBlank()

    /**
     * Check if first-time setup is complete. Setup requires: PIN set + signed in + calendar
     * selected. Used to determine whether to show Setup Wizard or Kiosk on launch.
     */
    val isSetupComplete: Boolean
        get() = isPinSet && isSignedIn && !selectedCalendarId.isNullOrBlank()

    /** Check if access token is expired or about to expire (within 5 minutes). */
    val isTokenExpired: Boolean
        get() {
            val expiration = tokenExpirationTime
            if (expiration == 0L) return true
            val bufferMs = 5 * 60 * 1000L // 5 minutes buffer
            return System.currentTimeMillis() >= (expiration - bufferMs)
        }

    /**
     * Store all tokens from OAuth response.
     *
     * @param accessToken The access token
     * @param refreshToken The refresh token (may be null on token refresh)
     * @param expiresInSeconds Token lifetime in seconds
     */
    fun storeTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long) {
        this.accessToken = accessToken
        // Only update refresh token if provided (not always returned on refresh)
        if (refreshToken != null) {
            this.refreshToken = refreshToken
        }
        this.tokenExpirationTime = System.currentTimeMillis() + (expiresInSeconds * 1000)
    }

    /** Clear all stored tokens and user data. Called on sign-out. */
    fun clearAll() {
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRATION)
                .remove(KEY_SELECTED_CALENDAR_ID)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_LAST_SYNC_TIME)
                .apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "memorylink_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRATION = "token_expiration"
        private const val KEY_SELECTED_CALENDAR_ID = "selected_calendar_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"

        // Admin PIN keys
        private const val KEY_ADMIN_PIN = "admin_pin"
        private const val KEY_FAILED_PIN_ATTEMPTS = "failed_pin_attempts"
        private const val KEY_PIN_LOCKOUT_END = "pin_lockout_end"

        // PIN lockout configuration
        const val MAX_PIN_ATTEMPTS = 3
        const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds

        // Manual config override keys
        private const val KEY_MANUAL_WAKE_TIME = "manual_wake_time"
        private const val KEY_MANUAL_SLEEP_TIME = "manual_sleep_time"
        private const val KEY_MANUAL_BRIGHTNESS = "manual_brightness"
        private const val KEY_MANUAL_TIME_FORMAT = "manual_time_format"
        private const val KEY_MANUAL_FONT_SIZE = "manual_font_size"
        private const val KEY_MANUAL_MESSAGE_SIZE = "manual_message_size"

        // Config event-derived settings keys (from [CONFIG] calendar events)
        private const val KEY_CONFIG_WAKE_TIME = "config_wake_time"
        private const val KEY_CONFIG_SLEEP_TIME = "config_sleep_time"
        private const val KEY_CONFIG_BRIGHTNESS = "config_brightness"
        private const val KEY_CONFIG_TIME_FORMAT = "config_time_format"
        private const val KEY_CONFIG_FONT_SIZE = "config_font_size"
        private const val KEY_CONFIG_MESSAGE_SIZE = "config_message_size"

        // Dynamic time config (SUNRISE/SUNSET references)
        private const val KEY_CONFIG_WAKE_SOLAR_REF = "config_wake_solar_ref"
        private const val KEY_CONFIG_WAKE_SOLAR_OFFSET = "config_wake_solar_offset"
        private const val KEY_CONFIG_SLEEP_SOLAR_REF = "config_sleep_solar_ref"
        private const val KEY_CONFIG_SLEEP_SOLAR_OFFSET = "config_sleep_solar_offset"
    }
}
