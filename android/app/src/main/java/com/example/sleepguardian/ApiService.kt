package com.example.sleepguardian

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val token: String, val message: String)
data class RegisterResponse(val message: String)

data class StartSessionRequest(val target_sleep_time: String, val target_wake_time: String)
data class StartSessionResponse(val session_id: Int, val message: String)
data class LogPenaltyRequest(val penalty_type: String)
data class GenericResponse(val message: String)

data class StatsResponse(
    val total_sessions: Int,
    val total_penalties: Int,
    val penalty_breakdown: Map<String, Int>,
    val current_streak: Int,
    val hearts: Int
)

data class SleepSessionItem(
    val id: Int,
    val start_time: String?,
    val end_time: String?,
    val target_sleep_time: String,
    val target_wake_time: String
)
data class HistoryResponse(val history: List<SleepSessionItem>)

interface ApiService {
    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("/api/auth/register")
    suspend fun register(@Body request: LoginRequest): RegisterResponse

    @POST("/api/sleep/start")
    suspend fun startSession(
        @Header("Authorization") token: String,
        @Body request: StartSessionRequest
    ): StartSessionResponse

    @POST("/api/sleep/end/{session_id}")
    suspend fun endSession(
        @Header("Authorization") token: String,
        @Path("session_id") sessionId: Int
    ): GenericResponse

    @POST("/api/sleep/penalty/{session_id}")
    suspend fun logPenalty(
        @Header("Authorization") token: String,
        @Path("session_id") sessionId: Int,
        @Body request: LogPenaltyRequest
    ): GenericResponse

    @GET("/api/sleep/stats")
    suspend fun getStats(
        @Header("Authorization") token: String
    ): StatsResponse

    @GET("/api/sleep/history")
    suspend fun getHistory(
        @Header("Authorization") token: String
    ): HistoryResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://sleepguardian-api.onrender.com"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}