package com.memorylink.data.auth

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for OAuth tokens and app settings using EncryptedSharedPreferences.
 *
 * Per .clinerules/20-android.md:
 * - Config Storage: EncryptedSharedPreferences for PIN, OAuth tokens, settings.
 * - Token encryption: AES-256-GCM via AndroidX Security library.
 *
 * Settings are unified - both admin panel and [CONFIG] calendar events write to the same fields.
 * Last write wins, no priority system.
 *
 * Note: SharedPreferences instance is injected from AppModule to avoid duplicate creation.
 */
@Singleton
class TokenStorage @Inject constructor(private val prefs: SharedPreferences) {

    // ========== OAuth Tokens ==========

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

    // ========== Calendar Selection ==========

    /** Selected calendar ID for event fetching. */
    var selectedCalendarId: String?
        get() = prefs.getString(KEY_SELECTED_CALENDAR_ID, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_CALENDAR_ID, value).apply()

    /** Selected calendar name (for display without needing API call). */
    var selectedCalendarName: String?
        get() = prefs.getString(KEY_SELECTED_CALENDAR_NAME, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_CALENDAR_NAME, value).apply()

    // ========== Remembered Calendar (for auto-reconnect on re-login) ==========

    /**
     * Remembered calendar ID from previous session. Used to auto-reconnect when user logs back in.
     * Unlike selectedCalendarId, this persists across sign-out.
     */
    var rememberedCalendarId: String?
        get() = prefs.getString(KEY_REMEMBERED_CALENDAR_ID, null)
        set(value) = prefs.edit().putString(KEY_REMEMBERED_CALENDAR_ID, value).apply()

    /** Remembered calendar name for display during auto-reconnect. */
    var rememberedCalendarName: String?
        get() = prefs.getString(KEY_REMEMBERED_CALENDAR_NAME, null)
        set(value) = prefs.edit().putString(KEY_REMEMBERED_CALENDAR_NAME, value).apply()

    /** User's email address (for display purposes). */
    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    /** Last successful sync timestamp in epoch millis. */
    var lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIME, value).apply()

    /** Last manual sync timestamp in epoch millis (for cooldown calculation). */
    var lastManualSyncTime: Long
        get() = prefs.getLong(KEY_LAST_MANUAL_SYNC_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_MANUAL_SYNC_TIME, value).apply()

    /**
     * Google Calendar API sync token for incremental sync.
     *
     * Per Google Calendar API docs:
     * - null = perform full sync
     * - non-null = perform incremental sync (only changes since last sync)
     * - Must be cleared when calendar selection changes
     * - 410 response means token expired, must clear and full sync
     */
    var syncToken: String?
        get() = prefs.getString(KEY_SYNC_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_SYNC_TOKEN, value).apply()

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

    // ========== App Settings (Unified) ==========
    // These settings are written by both admin panel and [CONFIG] calendar events.
    // Last write wins - no priority system.

    /** Wake time in HH:mm format. Written by admin panel or [CONFIG] WAKE HH:MM events. */
    var wakeTime: String?
        get() = prefs.getString(KEY_WAKE_TIME, null)
        set(value) = prefs.edit().putString(KEY_WAKE_TIME, value).apply()

    /** Sleep time in HH:mm format. Written by admin panel or [CONFIG] SLEEP HH:MM events. */
    var sleepTime: String?
        get() = prefs.getString(KEY_SLEEP_TIME, null)
        set(value) = prefs.edit().putString(KEY_SLEEP_TIME, value).apply()

    /**
     * Screen brightness (0-100), or -1 if using default. Written by admin panel or [CONFIG]
     * BRIGHTNESS events.
     */
    var brightness: Int
        get() = prefs.getInt(KEY_BRIGHTNESS, -1)
        set(value) = prefs.edit().putInt(KEY_BRIGHTNESS, value).apply()

    /**
     * Time format. null = use default (12h), true = 24h, false = 12h. Written by admin panel or
     * [CONFIG] TIME_FORMAT events.
     */
    var use24HourFormat: Boolean?
        get() {
            return if (prefs.contains(KEY_TIME_FORMAT)) {
                prefs.getBoolean(KEY_TIME_FORMAT, false)
            } else {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_TIME_FORMAT).apply()
            } else {
                prefs.edit().putBoolean(KEY_TIME_FORMAT, value).apply()
            }
        }

    /** Whether to show year in date display. null = use default (true). Written by admin panel. */
    var showYear: Boolean?
        get() {
            return if (prefs.contains(KEY_SHOW_YEAR)) {
                prefs.getBoolean(KEY_SHOW_YEAR, true)
            } else {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_SHOW_YEAR).apply()
            } else {
                prefs.edit().putBoolean(KEY_SHOW_YEAR, value).apply()
            }
        }

    /**
     * Whether to show events during sleep mode. null = use default (false). Written by admin panel.
     */
    var showEventsDuringSleep: Boolean?
        get() {
            return if (prefs.contains(KEY_SHOW_EVENTS_DURING_SLEEP)) {
                prefs.getBoolean(KEY_SHOW_EVENTS_DURING_SLEEP, false)
            } else {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_SHOW_EVENTS_DURING_SLEEP).apply()
            } else {
                prefs.edit().putBoolean(KEY_SHOW_EVENTS_DURING_SLEEP, value).apply()
            }
        }

    // ========== Holiday Calendar Settings ==========
    // Optional secondary calendar for holidays. Syncs weekly instead of every 15 minutes.

    /** Holiday calendar ID (optional). When set, holidays are synced from this calendar. */
    var holidayCalendarId: String?
        get() = prefs.getString(KEY_HOLIDAY_CALENDAR_ID, null)
        set(value) = prefs.edit().putString(KEY_HOLIDAY_CALENDAR_ID, value).apply()

    /** Holiday calendar name for display. */
    var holidayCalendarName: String?
        get() = prefs.getString(KEY_HOLIDAY_CALENDAR_NAME, null)
        set(value) = prefs.edit().putString(KEY_HOLIDAY_CALENDAR_NAME, value).apply()

    /** Whether to show holidays on the display. Default: true when holiday calendar is selected. */
    var showHolidays: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HOLIDAYS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HOLIDAYS, value).apply()

    /** Last holiday calendar sync timestamp. Used to enforce weekly sync. */
    var lastHolidaySyncTime: Long
        get() = prefs.getLong(KEY_LAST_HOLIDAY_SYNC_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_HOLIDAY_SYNC_TIME, value).apply()

    /** Sync token for holiday calendar incremental sync. */
    var holidaySyncToken: String?
        get() = prefs.getString(KEY_HOLIDAY_SYNC_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_HOLIDAY_SYNC_TOKEN, value).apply()

    /** Check if holiday calendar is configured. */
    val hasHolidayCalendar: Boolean
        get() = !holidayCalendarId.isNullOrBlank()

    /** Check if holiday sync is needed (more than 7 days since last sync). */
    val needsHolidaySync: Boolean
        get() {
            if (!hasHolidayCalendar) return false
            val elapsed = System.currentTimeMillis() - lastHolidaySyncTime
            return elapsed >= HOLIDAY_SYNC_INTERVAL_MS
        }

    /** Clear holiday calendar selection and related data. */
    fun clearHolidayCalendar() {
        prefs.edit()
                .remove(KEY_HOLIDAY_CALENDAR_ID)
                .remove(KEY_HOLIDAY_CALENDAR_NAME)
                .remove(KEY_LAST_HOLIDAY_SYNC_TIME)
                .remove(KEY_HOLIDAY_SYNC_TOKEN)
                .apply()
    }

    /** Clear all app settings (used when resetting or testing). */
    fun clearSettings() {
        prefs.edit()
                .remove(KEY_WAKE_TIME)
                .remove(KEY_SLEEP_TIME)
                .remove(KEY_BRIGHTNESS)
                .remove(KEY_TIME_FORMAT)
                .remove(KEY_SHOW_YEAR)
                .apply()
    }

    // ========== Helper Methods ==========

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

    /**
     * Clear all stored tokens and active session data. Called on sign-out. Preserves remembered
     * calendar preference for auto-reconnect on next login.
     */
    fun clearAll() {
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRATION)
                .remove(KEY_SELECTED_CALENDAR_ID)
                .remove(KEY_SELECTED_CALENDAR_NAME)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_LAST_SYNC_TIME)
                .remove(KEY_SYNC_TOKEN)
                .apply()
        // Note: remembered_calendar_id and remembered_calendar_name are intentionally preserved
    }

    companion object {
        private const val PREFS_FILE_NAME = "memorylink_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRATION = "token_expiration"
        private const val KEY_SELECTED_CALENDAR_ID = "selected_calendar_id"
        private const val KEY_SELECTED_CALENDAR_NAME = "selected_calendar_name"
        private const val KEY_REMEMBERED_CALENDAR_ID = "remembered_calendar_id"
        private const val KEY_REMEMBERED_CALENDAR_NAME = "remembered_calendar_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_LAST_MANUAL_SYNC_TIME = "last_manual_sync_time"
        private const val KEY_SYNC_TOKEN = "sync_token"

        // Admin PIN keys
        private const val KEY_ADMIN_PIN = "admin_pin"
        private const val KEY_FAILED_PIN_ATTEMPTS = "failed_pin_attempts"
        private const val KEY_PIN_LOCKOUT_END = "pin_lockout_end"

        // PIN lockout configuration
        const val MAX_PIN_ATTEMPTS = 3
        const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds

        // Unified app settings keys (written by both admin panel and [CONFIG] events)
        private const val KEY_WAKE_TIME = "wake_time"
        private const val KEY_SLEEP_TIME = "sleep_time"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_TIME_FORMAT = "time_format"
        private const val KEY_SHOW_YEAR = "show_year"
        private const val KEY_SHOW_EVENTS_DURING_SLEEP = "show_events_during_sleep"

        // Holiday calendar settings
        private const val KEY_HOLIDAY_CALENDAR_ID = "holiday_calendar_id"
        private const val KEY_HOLIDAY_CALENDAR_NAME = "holiday_calendar_name"
        private const val KEY_SHOW_HOLIDAYS = "show_holidays"
        private const val KEY_LAST_HOLIDAY_SYNC_TIME = "last_holiday_sync_time"
        private const val KEY_HOLIDAY_SYNC_TOKEN = "holiday_sync_token"

        /** Holiday calendar syncs weekly (7 days in millis). */
        const val HOLIDAY_SYNC_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
