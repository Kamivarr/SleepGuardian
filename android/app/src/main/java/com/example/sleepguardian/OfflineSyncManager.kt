package com.example.sleepguardian

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Manages the local persistence and synchronization of sleep sessions and penalty logs.
 * Ensures the application remains fully functional in offline environments (Local-First architecture).
 */
class OfflineSyncManager(context: Context) {
    private val prefs = context.getSharedPreferences("sleepguardian_offline", Context.MODE_PRIVATE)
    private val gson = Gson()

    // --- OFFLINE SESSION MANAGEMENT (When starting the app without network) ---

    /**
     * Initializes a local tracking session when the server is unreachable.
     */
    fun startOfflineSession(sleepTime: String, wakeTime: String) {
        prefs.edit()
            .putString("active_off_sleep", sleepTime)
            .putString("active_off_wake", wakeTime)
            .putString("active_off_penalties", "[]")
            .apply()
    }

    /**
     * Appends a discipline violation to the currently active offline session.
     */
    fun addPenaltyToActiveOfflineSession(penalty: String) {
        val json = prefs.getString("active_off_penalties", "[]")
        val type = object : TypeToken<List<String>>() {}.type
        val list: MutableList<String> = gson.fromJson(json, type) ?: mutableListOf()

        list.add(penalty)
        prefs.edit().putString("active_off_penalties", gson.toJson(list)).apply()
    }

    /**
     * Finalizes the active offline session and queues it for bulk server synchronization.
     */
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

    // --- QUEUE MANAGEMENT (When network drops during an active server session) ---

    private fun getQueuedFullSessions(): List<OfflineFullSession> {
        val json = prefs.getString("queued_full_sessions", "[]")
        val type = object : TypeToken<List<OfflineFullSession>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    /**
     * Queues a penalty for an active server session when a network timeout occurs.
     */
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

    /**
     * Queues a session termination request if the user wakes up offline.
     */
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

    /**
     * Sequentially attempts to upload all queued offline data to the backend API.
     * Retains unsynced payloads in local storage if network failures persist.
     * * TODO: Implement WorkManager for periodic background syncing if the queue grows too large.
     */
    suspend fun syncAll(token: String) {
        withContext(Dispatchers.IO) {
            // 1. Sync isolated penalties
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

            // 2. Sync isolated session terminations
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

            // 3. Sync full offline sessions (Start -> Penalties -> End)
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
    fun saveCachedStats(streak: Int, hearts: Int) {
        prefs.edit()
            .putInt("cached_streak", streak)
            .putInt("cached_hearts", hearts)
            .apply()
    }

    fun getCachedStreak(): Int {
        return prefs.getInt("cached_streak", 0)
    }

    fun getCachedHearts(): Int {
        return prefs.getInt("cached_hearts", 3)
    }
}