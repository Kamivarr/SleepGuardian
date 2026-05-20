package com.example.sleepguardian

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent storage for user authentication tokens and credentials using SharedPreferences.
 */
class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TOKEN_KEY = "jwt_token"
        private const val EMAIL_KEY = "saved_email"
        private const val PASSWORD_KEY = "saved_password"
    }

    fun saveToken(token: String) {
        prefs.edit().putString(TOKEN_KEY, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(TOKEN_KEY, null)
    }

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

    fun getSavedEmail(): String {
        return prefs.getString(EMAIL_KEY, "") ?: ""
    }

    fun getSavedPassword(): String {
        return prefs.getString(PASSWORD_KEY, "") ?: ""
    }
}