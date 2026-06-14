package com.example.sleepguardian

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Comprehensive unit tests for the OfflineSyncManager class.
 * Validates offline queue mechanisms, Local-First logic, anti-farming rules (Streak Freeze),
 * and asynchronous server synchronization (syncAll).
 */
class OfflineSyncManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private lateinit var offlineSyncManager: OfflineSyncManager

    @Before
    fun setUp() {
        // Ensure stability for method chaining within SharedPreferences
        every { context.getSharedPreferences("sleepguardian_offline", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } just Runs

        offlineSyncManager = OfflineSyncManager(context)
    }

    @After
    fun tearDown() {
        // Clear static mocks after each test execution to prevent state leakage
        unmockkAll()
    }

    // --- MEMORY AND COOLDOWN TESTS ---

    @Test
    fun testSaveAndGetCachedStats() {
        // Arrange
        every { sharedPreferences.getInt("cached_streak", 0) } returns 5
        every { sharedPreferences.getInt("cached_hearts", 3) } returns 2

        // Act
        offlineSyncManager.saveCachedStats(5, 2)

        // Assert
        verify { editor.putInt("cached_streak", 5) }
        verify { editor.putInt("cached_hearts", 2) }
        assertEquals(5, offlineSyncManager.getCachedStreak())
        assertEquals(2, offlineSyncManager.getCachedHearts())
    }

    @Test
    fun testCanStartNewSession_EnforcesCooldown() {
        // Arrange
        val currentTime = System.currentTimeMillis()
        // Simulate a session that ended 10 seconds ago
        every { sharedPreferences.getLong("last_session_end_timestamp", 0L) } returns (currentTime - 10_000L)

        // Act
        val allowed = offlineSyncManager.canStartNewSession()

        // Assert
        assertFalse("Session start rejected: cooldown period has not elapsed", allowed)
    }

    @Test
    fun testRecordSessionEnd() {
        // Act
        offlineSyncManager.recordSessionEnd()

        // Assert
        verify { editor.putLong("last_session_end_timestamp", any()) }
    }

    // --- REWARD LOGIC TESTS (BRANCH COVERAGE) ---

    @Test
    fun testRecordSuccessfulSession_StreakZero_StartsAtOne() {
        // Arrange
        every { sharedPreferences.getInt("cached_streak", 0) } returns 0
        every { sharedPreferences.getInt("cached_hearts", 3) } returns 1

        // Act
        offlineSyncManager.recordSuccessfulSession()

        // Assert: Expect a 1-day streak increment and +1 heart regeneration
        verify { editor.putInt("cached_streak", 1) }
        verify { editor.putInt("cached_hearts", 2) }
    }

    @Test
    fun testRecordSuccessfulSession_DifferentDate_IncrementsStreak() {
        // Arrange
        every { sharedPreferences.getInt("cached_streak", 0) } returns 5
        every { sharedPreferences.getInt("cached_hearts", 3) } returns 3
        every { sharedPreferences.getString("last_success_date", "") } returns "2000-01-01"

        // Act
        offlineSyncManager.recordSuccessfulSession()

        // Assert: Streak increments, Hearts are capped at max(3)
        verify { editor.putInt("cached_streak", 6) }
        verify { editor.putInt("cached_hearts", 3) }
    }

    @Test
    fun testRecordSuccessfulSession_SameDate_KeepsStreak() {
        // Arrange
        every { sharedPreferences.getInt("cached_streak", 0) } returns 5
        every { sharedPreferences.getInt("cached_hearts", 3) } returns 1
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        every { sharedPreferences.getString("last_success_date", "") } returns today

        // Act
        offlineSyncManager.recordSuccessfulSession()

        // Assert: Streak is locked against farming (remains unchanged)
        verify { editor.putInt("cached_streak", 5) }
        verify { editor.putInt("cached_hearts", 2) }
    }

    // --- PENALTY LOGIC TESTS (STREAK FREEZE) ---

    @Test
    fun testRecordFailedSession_WithHearts_LosesHeartKeepsStreak() {
        // Arrange
        every { sharedPreferences.getInt("cached_streak", 0) } returns 10
        every { sharedPreferences.getInt("cached_hearts", 3) } returns 2

        // Act
        val streakSaved = offlineSyncManager.recordFailedSession()

        // Assert
        assertTrue("Returned true - streak protected", streakSaved)
        verify { editor.putInt("cached_streak", 10) }
        verify { editor.putInt("cached_hearts", 1) } // Heart deduction
    }

    @Test
    fun testRecordFailedSession_NoHearts_ResetsStreakAndHearts() {
        // Arrange
        every { sharedPreferences.getInt("cached_streak", 0) } returns 10
        every { sharedPreferences.getInt("cached_hearts", 3) } returns 0

        // Act
        val streakSaved = offlineSyncManager.recordFailedSession()

        // Assert
        assertFalse("Returned false - no hearts remaining", streakSaved)
        verify { editor.putInt("cached_streak", 0) } // Severe reset
        verify { editor.putInt("cached_hearts", 0) }
    }

    // --- OFFLINE QUEUE LOGIC TESTS ---

    @Test
    fun testStartOfflineSession_WritesCorrectKeys() {
        // Act
        offlineSyncManager.startOfflineSession("23:00", "07:00")

        // Assert
        verify { editor.putString("active_off_sleep", "23:00") }
        verify { editor.putString("active_off_wake", "07:00") }
        verify { editor.putString("active_off_penalties", "[]") }
    }

    @Test
    fun testAddPenaltyToActiveOfflineSession() {
        // Arrange
        every { sharedPreferences.getString("active_off_penalties", "[]") } returns "[\"Kurtyna\"]"

        // Act
        offlineSyncManager.addPenaltyToActiveOfflineSession("Komar")

        // Assert
        verify { editor.putString("active_off_penalties", "[\"Kurtyna\",\"Komar\"]") }
    }

    @Test
    fun testEndOfflineSessionAndQueue() {
        // Arrange
        every { sharedPreferences.getString("active_off_sleep", "22:00") } returns "22:00"
        every { sharedPreferences.getString("active_off_wake", "06:00") } returns "06:00"
        every { sharedPreferences.getString("active_off_penalties", "[]") } returns "[\"Kara\"]"
        every { sharedPreferences.getString("queued_full_sessions", "[]") } returns "[]"

        // Act
        offlineSyncManager.endOfflineSessionAndQueue()

        // Assert
        verify { editor.putString("queued_full_sessions", match { it.contains("Kara") }) }
        verify { editor.remove("active_off_sleep") }
    }

    @Test
    fun testQueueStandaloneEnd() {
        // Arrange
        every { sharedPreferences.getString("queued_standalone_ends", "[]") } returns "[]"

        // Act
        offlineSyncManager.queueStandaloneEnd(99)

        // Assert
        verify { editor.putString("queued_standalone_ends", "[99]") }
    }

    @Test
    fun testQueueStandalonePenalty() {
        // Arrange
        every { sharedPreferences.getString("queued_standalone_penalties", "[]") } returns "[]"

        // Act
        offlineSyncManager.queueStandalonePenalty(1, "Brak dyscypliny")

        // Assert
        verify { editor.putString("queued_standalone_penalties", match { it.contains("Brak dyscypliny") }) }
    }

    // --- SERVER SYNCHRONIZATION TESTS (ASYNC) ---

    @Test
    fun testSyncAll_ProcessesQueuesSuccessfully() = runBlocking {
        // 1. Prepare mock for the API Interface
        val apiServiceMock = mockk<ApiService>()
        coEvery { apiServiceMock.logPenalty(any(), any(), any()) } returns GenericResponse("OK")
        coEvery { apiServiceMock.endSession(any(), any()) } returns GenericResponse("OK")
        coEvery { apiServiceMock.startSession(any(), any()) } returns StartSessionResponse(99, "OK")

        // 2. Replace the static RetrofitClient object with our mock
        mockkObject(RetrofitClient)
        every { RetrofitClient.apiService } returns apiServiceMock

        // 3. Simulate pending data residing in local storage
        every { sharedPreferences.getString("queued_standalone_penalties", "[]") } returns "[{\"sessionId\":1,\"penaltyType\":\"Kara\"}]"
        every { sharedPreferences.getString("queued_standalone_ends", "[]") } returns "[1]"
        every { sharedPreferences.getString("queued_full_sessions", "[]") } returns "[{\"sleepTime\":\"22:00\",\"wakeTime\":\"06:00\",\"penalties\":[\"Kara\"]}]"

        // Act - Execute synchronization logic
        offlineSyncManager.syncAll("dummy_token")

        // Assert - Verify that the loops hit the API the required number of times
        coVerify(exactly = 1) { apiServiceMock.logPenalty(any(), 1, any()) }
        coVerify(exactly = 1) { apiServiceMock.endSession(any(), 1) }
        coVerify(exactly = 1) { apiServiceMock.startSession(any(), any()) }
    }

    @Test
    fun testUpdateLocalStatsFromServer_Success_UpdatesMaxValues() = runBlocking {
        // Arrange
        val apiServiceMock = mockk<ApiService>()
        // Server returns a higher streak, but fewer hearts
        coEvery { apiServiceMock.getStats(any()) } returns StatsResponse(10, 5, emptyMap(), 15, 1)

        mockkObject(RetrofitClient)
        every { RetrofitClient.apiService } returns apiServiceMock

        // Local cache has a lower streak, but more hearts (e.g., earned offline)
        every { sharedPreferences.getInt("cached_streak", 0) } returns 5
        every { sharedPreferences.getInt("cached_hearts", 3) } returns 3

        // Act
        offlineSyncManager.updateLocalStatsFromServer("dummy_token")

        // Assert: Saves max(local, server) -> Streak 15, Hearts 3
        verify { editor.putInt("cached_streak", 15) }
        verify { editor.putInt("cached_hearts", 3) }
    }

    @Test
    fun testUpdateLocalStatsFromServer_Exception_IgnoresError() = runBlocking {
        // Arrange
        val apiServiceMock = mockk<ApiService>()
        coEvery { apiServiceMock.getStats(any()) } throws Exception("Network Error")

        mockkObject(RetrofitClient)
        every { RetrofitClient.apiService } returns apiServiceMock

        // Act
        // The method should swallow the exception in the catch block and prevent an app crash
        offlineSyncManager.updateLocalStatsFromServer("dummy_token")

        // Assert: Ensure the cache was not overwritten with garbage data
        verify(exactly = 0) { editor.putInt("cached_streak", any()) }
    }
}