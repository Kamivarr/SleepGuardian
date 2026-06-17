package com.prz.sleepguardian

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying the proper saving, reading, and clearing
 * of authentication and session data within TokenManager.
 */
class TokenManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private lateinit var tokenManager: TokenManager

    @Before
    fun setUp() {
        // Configure mock behavior for Android's SharedPreferences
        every { context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor

        tokenManager = TokenManager(context)
    }

    @Test
    fun testSaveAndGetToken() {
        // Arrange
        val testToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        every { sharedPreferences.getString("jwt_token", null) } returns testToken

        // Act
        tokenManager.saveToken(testToken)

        // Assert
        verify { editor.putString("jwt_token", testToken) }
        assertEquals(testToken, tokenManager.getToken())
    }

    @Test
    fun testClearToken_RemovesTokenFromPreferences() {
        // Act
        tokenManager.clearToken()

        // Assert
        verify { editor.remove("jwt_token") }
    }

    @Test
    fun testSaveAndGetCredentials() {
        // Arrange
        val email = "student@prz.edu.pl"
        val password = "securePassword123"
        every { sharedPreferences.getString("saved_email", "") } returns email
        every { sharedPreferences.getString("saved_password", "") } returns password

        // Act
        tokenManager.saveCredentials(email, password)

        // Assert
        verify { editor.putString("saved_email", email) }
        verify { editor.putString("saved_password", password) }
        assertEquals(email, tokenManager.getSavedEmail())
        assertEquals(password, tokenManager.getSavedPassword())
    }

    @Test
    fun testSaveAndGetSessionId() {
        // Arrange
        val activeSessionId = 42
        every { sharedPreferences.getInt("active_session_id", -1) } returns activeSessionId

        // Act
        tokenManager.saveSessionId(activeSessionId)

        // Assert
        verify { editor.putInt("active_session_id", activeSessionId) }
        assertEquals(activeSessionId, tokenManager.getSessionId())
    }
}