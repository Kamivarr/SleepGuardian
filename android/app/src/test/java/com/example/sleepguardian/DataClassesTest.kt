package com.example.sleepguardian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for Data Transfer Object (DTO) classes.
 * Ensures that data structures correctly store and return assigned values,
 * which is critical for JSON serialization/deserialization via Retrofit.
 */
class DataClassesTest {

    @Test
    fun testLoginRequest() {
        val request = LoginRequest("student@prz.edu.pl", "haslo123")
        assertEquals("student@prz.edu.pl", request.email)
        assertEquals("haslo123", request.password)
    }

    @Test
    fun testLoginResponse() {
        val response = LoginResponse("fake_jwt_token_123", "Logowanie udane")
        assertEquals("fake_jwt_token_123", response.token)
        assertEquals("Logowanie udane", response.message)
    }

    @Test
    fun testRegisterResponse() {
        val response = RegisterResponse("Konto utworzone")
        assertEquals("Konto utworzone", response.message)
    }

    @Test
    fun testSleepSessionItem() {
        val item = SleepSessionItem(
            id = 42,
            start_time = "2026-06-14T22:00:00",
            end_time = "2026-06-15T06:00:00",
            target_sleep_time = "22:00",
            target_wake_time = "06:00"
        )

        assertEquals(42, item.id)
        assertEquals("2026-06-14T22:00:00", item.start_time)
        assertEquals("2026-06-15T06:00:00", item.end_time)
        assertEquals("22:00", item.target_sleep_time)
        assertEquals("06:00", item.target_wake_time)
    }

    @Test
    fun testHistoryResponse() {
        val item = SleepSessionItem(
            id = 1,
            start_time = "2026-06-14",
            end_time = null,
            target_sleep_time = "23:00",
            target_wake_time = "07:00"
        )
        val response = HistoryResponse(listOf(item))

        assertNotNull(response.history)
        assertEquals(1, response.history.size)
        assertEquals(1, response.history[0].id)
    }
}