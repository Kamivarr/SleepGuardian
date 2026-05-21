package com.example.sleepguardian

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a penalty registered when the device was offline but an active server session existed.
 */
data class StandalonePenalty(val sessionId: Int, val penaltyType: String)

/**
 * Represents a complete sleep session (including start, configuration, and incurred penalties)
 * created entirely while the device was disconnected from the network.
 */
data class OfflineFullSession(val sleepTime: String, val wakeTime: String, val penalties: List<String>)

/**
 * Manages local persistence for offline functionality and gamification rules.
 * Implements anti-farming constraints and ensures Local-First UI responsiveness.
 */
class OfflineSyncManager(context: Context) {
    private val prefs = context.getSharedPreferences("sleepguardian_offline", Context.MODE_PRIVATE)
    private val gson = Gson()

    // --- ANTI-FARMING & SESSION CONTROLS ---

    /**
     * Checks if the user is allowed to start a new session.
     * Prevents farming hearts by enforcing a cooldown period between sessions.
     */
    fun canStartNewSession(): Boolean {
        val lastEnd = prefs.getLong("last_session_end_timestamp", 0L)
        val currentTime = System.currentTimeMillis()

        // TODO for production: Change 15_000L (15 sec test) to 12 * 60 * 60 * 1000L (12 hours)
        val cooldownMs = 15_000L

        return (currentTime - lastEnd) > cooldownMs
    }

    /**
     * Records the exact time a session was terminated to enforce future cooldowns.
     */
    fun recordSessionEnd() {
        prefs.edit().putLong("last_session_end_timestamp", System.currentTimeMillis()).apply()
    }

    // --- OFFLINE SESSION LOGIC ---

    fun startOfflineSession(sleepTime: String, wakeTime: String) {
        prefs.edit()
            .putString("active_off_sleep", sleepTime)
            .putString("active_off_wake", wakeTime)
            .putString("active_off_penalties", "[]")
            .apply()
    }

    fun addPenaltyToActiveOfflineSession(penalty: String) {
        val json = prefs.getString("active_off_penalties", "[]")
        val type = object : TypeToken<List<String>>() {}.type
        val list: MutableList<String> = gson.fromJson(json, type) ?: mutableListOf()

        list.add(penalty)
        prefs.edit().putString("active_off_penalties", gson.toJson(list)).apply()
    }

    fun endOfflineSessionAndQueue() {
        val sleep = prefs.getString("active_off_sleep", "22:00") ?: "22:00"
        val wake = prefs.getString("active_off_wake", "06:00") ?: "06:00"
        val pJson = prefs.getString("active_off_penalties", "[]")
        val pType = object : TypeToken<List<String>>() {}.type
        val penalties: List<String> = gson.fromJson(pJson, pType) ?: emptyList()

        val queued = getQueuedFullSessions().toMutableList()
        queued.add(OfflineFullSession(sleep, wake, penalties))

        prefs.edit()
            .putString("queued_full_sessions", gson.toJson(queued))
            .remove("active_off_sleep")
            .remove("active_off_wake")
            .remove("active_off_penalties")
            .apply()
    }

    // --- QUEUE MANAGEMENT ---

    private fun getQueuedFullSessions(): List<OfflineFullSession> {
        val json = prefs.getString("queued_full_sessions", "[]")
        val type = object : TypeToken<List<OfflineFullSession>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun queueStandalonePenalty(sessionId: Int, penalty: String) {
        val queued = getQueuedStandalonePenalties().toMutableList()
        queued.add(StandalonePenalty(sessionId, penalty))
        prefs.edit().putString("queued_standalone_penalties", gson.toJson(queued)).apply()
    }

    private fun getQueuedStandalonePenalties(): List<StandalonePenalty> {
        val json = prefs.getString("queued_standalone_penalties", "[]")
        val type = object : TypeToken<List<StandalonePenalty>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun queueStandaloneEnd(sessionId: Int) {
        val queued = getQueuedStandaloneEnds().toMutableList()
        queued.add(sessionId)
        prefs.edit().putString("queued_standalone_ends", gson.toJson(queued)).apply()
    }

    private fun getQueuedStandaloneEnds(): List<Int> {
        val json = prefs.getString("queued_standalone_ends", "[]")
        val type = object : TypeToken<List<Int>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    // --- CORE SYNCHRONIZATION ENGINE ---

    suspend fun syncAll(token: String) {
        withContext(Dispatchers.IO) {
            val standalonePenalties = getQueuedStandalonePenalties()
            val successfulPenalties = mutableListOf<StandalonePenalty>()
            for (p in standalonePenalties) {
                try {
                    RetrofitClient.apiService.logPenalty("Bearer $token", p.sessionId, LogPenaltyRequest(p.penaltyType))
                    successfulPenalties.add(p)
                } catch (e: Exception) { break }
            }
            if (successfulPenalties.isNotEmpty()) {
                val remaining = standalonePenalties - successfulPenalties.toSet()
                prefs.edit().putString("queued_standalone_penalties", gson.toJson(remaining)).apply()
            }

            val standaloneEnds = getQueuedStandaloneEnds()
            val successfulEnds = mutableListOf<Int>()
            for (eId in standaloneEnds) {
                try {
                    RetrofitClient.apiService.endSession("Bearer $token", eId)
                    successfulEnds.add(eId)
                } catch (e: Exception) { break }
            }
            if (successfulEnds.isNotEmpty()) {
                val remaining = standaloneEnds - successfulEnds.toSet()
                prefs.edit().putString("queued_standalone_ends", gson.toJson(remaining)).apply()
            }

            val fullSessions = getQueuedFullSessions()
            val successfulFullSessions = mutableListOf<OfflineFullSession>()
            for (fs in fullSessions) {
                try {
                    val res = RetrofitClient.apiService.startSession("Bearer $token", StartSessionRequest(fs.sleepTime, fs.wakeTime))
                    val newId = res.session_id
                    for (p in fs.penalties) {
                        RetrofitClient.apiService.logPenalty("Bearer $token", newId, LogPenaltyRequest(p))
                    }
                    RetrofitClient.apiService.endSession("Bearer $token", newId)
                    successfulFullSessions.add(fs)
                } catch (e: Exception) { break }
            }
            if (successfulFullSessions.isNotEmpty()) {
                val remaining = fullSessions - successfulFullSessions.toSet()
                prefs.edit().putString("queued_full_sessions", gson.toJson(remaining)).apply()
            }
        }
    }

    // --- GAMIFICATION LOCAL-FIRST LOGIC ---

    fun saveCachedStats(streak: Int, hearts: Int) {
        prefs.edit()
            .putInt("cached_streak", streak)
            .putInt("cached_hearts", hearts)
            .apply()
    }

    fun getCachedStreak(): Int = prefs.getInt("cached_streak", 0)
    fun getCachedHearts(): Int = prefs.getInt("cached_hearts", 3)

    /**
     * Optimized reward calculation.
     * Adds +1 to the current streak (max once a day) and restores +1 heart (max 3).
     * Provides immediate UI feedback without waiting for server response.
     */
    fun recordSuccessfulSession() {
        val currentHearts = getCachedHearts()
        val currentStreak = getCachedStreak()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastSuccessDate = prefs.getString("last_success_date", "")

        val newStreak = if (lastSuccessDate != today) {
            prefs.edit().putString("last_success_date", today).apply()
            currentStreak + 1
        } else {
            currentStreak // Prevents farming streaks multiple times in a single day
        }

        // Hearts can be restored even multiple times a day (up to max 3)
        val newHearts = minOf(3, currentHearts + 1)
        saveCachedStats(newStreak, newHearts)
    }

    /**
     * Optimized penalty processing with "Streak Freeze" logic.
     * - If the user has >0 hearts: Subtracts 1 heart, protects the streak.
     * - If the user has 0 hearts: Drops the streak to 0.
     * Returns true if a heart saved the streak, false if the streak was lost.
     */
    fun recordFailedSession(): Boolean {
        val currentHearts = getCachedHearts()
        val currentStreak = getCachedStreak()

        return if (currentHearts > 0) {
            // Streak Freeze triggered: lose a heart, keep the streak
            saveCachedStats(currentStreak, currentHearts - 1)
            true
        } else {
            // No hearts left: reset streak to 0, hearts remain 0
            saveCachedStats(0, 0)
            false
        }
    }
}