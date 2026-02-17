package com.memorylink.data.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.CalendarScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Manages Google OAuth2 authentication for Calendar API (calendar.readonly). */
@Singleton
class GoogleAuthManager
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val tokenStorage: TokenStorage
) {
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso =
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestServerAuthCode(getWebClientId(), true) // forceCodeForRefreshToken
                        .requestEmail()
                        .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
                        .build()
        GoogleSignIn.getClient(context, gso)
    }

    /** Result of authentication attempt. */
    sealed class AuthResult {
        data class Success(val email: String) : AuthResult()
        data class Error(val message: String, val exception: Exception? = null) : AuthResult()
        data object Cancelled : AuthResult()
        data object NeedsSignIn : AuthResult()
    }

    /** Get sign-in intent for launching from Activity with startActivityForResult. */
    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    /** Handle result from sign-in activity. */
    suspend fun handleSignInResult(data: Intent?): AuthResult =
            withContext(Dispatchers.IO) {
                try {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                    val account = task.getResult(ApiException::class.java)

                    processSignInAccount(account)
                } catch (e: ApiException) {
                    when (e.statusCode) {
                        12501 -> { // Sign-in cancelled
                            Log.d(TAG, "Sign-in cancelled by user")
                            AuthResult.Cancelled
                        }
                        else -> {
                            Log.e(TAG, "Sign-in failed with code: ${e.statusCode}", e)
                            AuthResult.Error("Sign-in failed: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sign-in failed", e)
                    AuthResult.Error("Sign-in failed: ${e.message}", e)
                }
            }

    /** Process a successful sign-in account. */
    private suspend fun processSignInAccount(account: GoogleSignInAccount): AuthResult {
        val serverAuthCode = account.serverAuthCode
        if (serverAuthCode == null) {
            Log.e(TAG, "No server auth code received")
            return AuthResult.Error("No authorization code received. Please try signing in again.")
        }

        val email = account.email ?: "Unknown"
        tokenStorage.userEmail = email

        // Exchange auth code for tokens
        return try {
            exchangeAuthCodeForTokens(serverAuthCode)
            Log.d(TAG, "Successfully signed in as: $email")
            AuthResult.Success(email)
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failed", e)
            AuthResult.Error("Failed to complete sign-in: ${e.message}", e)
        }
    }

    /** Check if user is already signed in with valid credentials. */
    suspend fun checkExistingSignIn(): AuthResult =
            withContext(Dispatchers.IO) {
                val lastAccount = GoogleSignIn.getLastSignedInAccount(context)

                if (lastAccount != null && tokenStorage.isSignedIn) {
                    // Check if token is still valid or can be refreshed
                    if (tokenStorage.isTokenExpired) {
                        val refreshed = refreshAccessToken()
                        if (refreshed) {
                            AuthResult.Success(lastAccount.email ?: "Unknown")
                        } else {
                            AuthResult.NeedsSignIn
                        }
                    } else {
                        AuthResult.Success(lastAccount.email ?: "Unknown")
                    }
                } else {
                    AuthResult.NeedsSignIn
                }
            }

    /** Exchange authorization code for access and refresh tokens. */
    private suspend fun exchangeAuthCodeForTokens(authCode: String) =
            withContext(Dispatchers.IO) {
                val tokenResponse =
                        GoogleAuthorizationCodeTokenRequest(
                                        NetHttpTransport(),
                                        GsonFactory.getDefaultInstance(),
                                        "https://oauth2.googleapis.com/token",
                                        getWebClientId(),
                                        getWebClientSecret(),
                                        authCode,
                                        "" // No redirect URI for Android apps
                                )
                                .execute()

                tokenStorage.storeTokens(
                        accessToken = tokenResponse.accessToken,
                        refreshToken = tokenResponse.refreshToken,
                        expiresInSeconds = tokenResponse.expiresInSeconds ?: 3600L
                )

                Log.d(TAG, "Tokens stored successfully")
            }

    /** Refresh access token using stored refresh token. Returns true on success. */
    suspend fun refreshAccessToken(): Boolean =
            withContext(Dispatchers.IO) {
                val refreshToken = tokenStorage.refreshToken
                if (refreshToken.isNullOrBlank()) {
                    Log.w(TAG, "No refresh token available")
                    return@withContext false
                }

                try {
                    val tokenResponse =
                            GoogleRefreshTokenRequest(
                                            NetHttpTransport(),
                                            GsonFactory.getDefaultInstance(),
                                            refreshToken,
                                            getWebClientId(),
                                            getWebClientSecret()
                                    )
                                    .execute()

                    tokenStorage.storeTokens(
                            accessToken = tokenResponse.accessToken,
                            refreshToken = null, // Refresh token not returned on refresh
                            expiresInSeconds = tokenResponse.expiresInSeconds ?: 3600L
                    )

                    Log.d(TAG, "Token refreshed successfully")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Token refresh failed", e)
                    false
                }
            }

    /** Get valid access token, refreshing if necessary. */
    suspend fun getValidAccessToken(): String? {
        if (!tokenStorage.isSignedIn) {
            return null
        }

        if (tokenStorage.isTokenExpired) {
            val refreshed = refreshAccessToken()
            if (!refreshed) {
                return null
            }
        }

        return tokenStorage.accessToken
    }

    /** Sign out and clear stored credentials. */
    suspend fun signOut() =
            withContext(Dispatchers.IO) {
                try {
                    googleSignInClient.signOut()
                    tokenStorage.clearAll()
                    Log.d(TAG, "Signed out successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Sign-out error", e)
                    // Still clear local storage even if sign-out fails
                    tokenStorage.clearAll()
                }
            }

    /** Revoke access and sign out completely. */
    suspend fun revokeAccess() =
            withContext(Dispatchers.IO) {
                try {
                    googleSignInClient.revokeAccess()
                    tokenStorage.clearAll()
                    Log.d(TAG, "Access revoked successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Revoke access error", e)
                    tokenStorage.clearAll()
                }
            }

    /** Check if user is currently signed in. */
    val isSignedIn: Boolean
        get() = tokenStorage.isSignedIn

    /** Get the signed-in user's email. */
    val userEmail: String?
        get() = tokenStorage.userEmail

    /**
     * Get OAuth Web Client ID from BuildConfig.
     *
     * IMPORTANT: For Calendar API access with server auth code exchange, you need a WEB client ID
     * (not Android client ID).
     *
     * Setup: Add credentials to local.properties (see local.properties.example)
     */
    private fun getWebClientId(): String {
        val clientId = com.memorylink.BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank() || clientId.startsWith("YOUR_")) {
            Log.w(TAG, "GOOGLE_WEB_CLIENT_ID not configured - add it to local.properties")
        }
        return clientId
    }

    private fun getWebClientSecret(): String {
        val secret = com.memorylink.BuildConfig.GOOGLE_WEB_CLIENT_SECRET
        if (secret.isBlank() || secret.startsWith("YOUR_")) {
            Log.w(TAG, "GOOGLE_WEB_CLIENT_SECRET not configured - add it to local.properties")
        }
        return secret
    }

    companion object {
        private const val TAG = "GoogleAuthManager"
    }
}
