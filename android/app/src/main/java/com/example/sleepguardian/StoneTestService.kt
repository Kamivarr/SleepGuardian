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
import kotlin.math.sqrt

/**
 * Foreground service for the "Test Kamienia" penalty mode.
 * Uses hardware accelerometer to detect movement.
 */
class StoneTestService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var countdownTimer: CountDownTimer? = null

    private val requiredTimeMs: Long = 5000

    // Margines błędu. Grawitacja to ~9.81. Odchylenia to ruch.
    private val upperThreshold = 10.5f
    private val lowerThreshold = 9.0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        initializeForegroundService()

        // Inicjalizacja czujnika
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
            .setContentText("Nie dotykaj telefonu przez 3 minuty.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()

        startForeground(2, notification) // ID = 2, żeby nie gryzło się z Kurtyną
    }

    private fun startTimer() {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(requiredTimeMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Tutaj w przyszłości możemy aktualizować powiadomienie o pozostały czas
            }

            override fun onFinish() {
                // Kara minęła, telefon leżał nieruchomo
                Toast.makeText(applicationContext, "Test zaliczony! Kara wyłączona.", Toast.LENGTH_LONG).show()
                stopSelf() // Wyłącza serwis
            }
        }.start()
    }

    // Funkcja wywoływana dziesiątki razy na sekundę przez system Android
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            // Obliczenie wektora przyspieszenia
            val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            // Jeśli telefon się poruszył (wstrząs) - resetujemy timer
            if (magnitude > upperThreshold || magnitude < lowerThreshold) {
                startTimer()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Nie potrzebujemy tego obsługiwać
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        countdownTimer?.cancel()
    }
}