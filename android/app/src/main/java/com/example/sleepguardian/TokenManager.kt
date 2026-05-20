package com.example.sleepguardian

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent storage for user authentication tokens, credentials, and active session data
 * using the Android SharedPreferences API.
 */
class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TOKEN_KEY = "jwt_token"
        private const val EMAIL_KEY = "saved_email"
        private const val PASSWORD_KEY = "saved_password"
        private const val SESSION_ID_KEY = "active_session_id"
    }

    /**
     * Persists the JWT authentication token.
     */
    fun saveToken(token: String) {
        prefs.edit().putString(TOKEN_KEY, token).apply()
    }

    /**
     * Retrieves the stored JWT token. Returns null if no user is authenticated.
     */
    fun getToken(): String? {
        return prefs.getString(TOKEN_KEY, null)
    }

    /**
     * Clears the authentication token, effectively logging the user out.
     */
    fun clearToken() {
        prefs.edit().remove(TOKEN_KEY).apply()
    }

    /**
     * Persists user credentials to pre-fill the login form during subsequent sessions.
     */
    fun saveCredentials(email: String, pass: String) {
        prefs.edit()
            .putString(EMAIL_KEY, email)
            .putString(PASSWORD_KEY, pass)
            .apply()
    }

    fun getSavedEmail(): String = prefs.getString(EMAIL_KEY, "") ?: ""
    fun getSavedPassword(): String = prefs.getString(PASSWORD_KEY, "") ?: ""

    /**
     * Persists the ID of the current sleep session returned by the backend API.
     */
    fun saveSessionId(id: Int) {
        prefs.edit().putInt(SESSION_ID_KEY, id).apply()
    }

    /**
     * Retrieves the active session ID. Returns -1 if no session is active.
     */
    fun getSessionId(): Int = prefs.getInt(SESSION_ID_KEY, -1)
}