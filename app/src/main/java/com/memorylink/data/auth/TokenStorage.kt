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

    /** Check if user is signed in (has refresh token). */
    val isSignedIn: Boolean
        get() = !refreshToken.isNullOrBlank()

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
    }
}
