package com.example.sleepguardian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Foreground service for the "Test Kamienia" penalty mode.
 * Evaluates accelerometer data and utilizes a 30-second cooldown before reporting subsequent penalties.
 */
class StoneTestService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var countdownTimer: CountDownTimer? = null

    private val requiredTimeMs: Long = 5000
    private var lastPenaltyTime: Long = 0

    private val upperThreshold = 10.5f
    private val lowerThreshold = 9.0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        initializeForegroundService()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            Toast.makeText(this, "Test Kamienia aktywny. Odłóż telefon!", Toast.LENGTH_LONG).show()
            startTimer()
        } else {
            Toast.makeText(this, "Błąd: Brak akcelerometru w urządzeniu.", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun initializeForegroundService() {
        val channelId = "sleep_guardian_sensor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SleepGuardian Sensors",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Test Kamienia Trwa")
            .setContentText("Nie dotykaj telefonu przez wyznaczony czas.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()

        startForeground(2, notification)
    }

    private fun startTimer() {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(requiredTimeMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                Toast.makeText(applicationContext, "Test zaliczony! Kara wyłączona.", Toast.LENGTH_LONG).show()
                stopSelf()
            }
        }.start()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            if (magnitude > upperThreshold || magnitude < lowerThreshold) {
                val currentTime = System.currentTimeMillis()

                // 30 seconds cooldown to prevent API spamming
                if (currentTime - lastPenaltyTime > 30000) {
                    reportPenalty("Test Kamienia")
                    lastPenaltyTime = currentTime
                }

                startTimer()
            }
        }
    }

    private fun reportPenalty(penaltyType: String) {
        val tokenManager = TokenManager(applicationContext)
        val token = tokenManager.getToken()
        val sessionId = tokenManager.getSessionId()

        if (token != null && sessionId != -1) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val request = LogPenaltyRequest(penaltyType)
                    RetrofitClient.apiService.logPenalty("Bearer $token", sessionId, request)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        countdownTimer?.cancel()
    }
}