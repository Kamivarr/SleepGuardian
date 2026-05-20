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
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.sqrt

/**
 * Foreground service for the "Test Kamienia" penalty mode.
 * Dynamically evaluates hardware accelerometer to trigger a 30-second warning sequence.
 * Failing to put the device down aborts the entire sleep session negatively.
 */
class StoneTestService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val upperThreshold = 10.5f
    private val lowerThreshold = 9.0f

    private var warningJob: Job? = null
    private var stationaryJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        initializeForegroundService()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            Toast.makeText(this, "Błąd: Brak akcelerometru w urządzeniu.", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun initializeForegroundService() {
        val channelId = "sleep_guardian_sensor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SleepGuardian Sensors", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Test Kamienia")
            .setContentText("Ochrona jest aktywna. Każde podniesienie telefonu wywołuje ostrzeżenie.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()

        startForeground(2, notification)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]
            val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val isMoving = magnitude > upperThreshold || magnitude < lowerThreshold

            if (isMoving) {
                // Jeśli zarejestrowano ruch: anuluj zadanie stacjonarne (resetujące) i rozpocznij ostrzeżenia
                stationaryJob?.cancel()
                stationaryJob = null

                if (warningJob == null) {
                    startWarningSequence()
                }
            } else {
                // Jeśli telefon jest nieruchomy, poczekaj 2 sekundy i anuluj proces ostrzeżeń
                if (warningJob != null && stationaryJob == null) {
                    stationaryJob = CoroutineScope(Dispatchers.Default).launch {
                        delay(2000)
                        warningJob?.cancel()
                        warningJob = null
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Telefon odłożony. Reset licznika.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * Executes the 30-second penalty verification pipeline.
     * Notifies the user every 10 seconds.
     */
    private fun startWarningSequence() {
        warningJob = CoroutineScope(Dispatchers.Main).launch {
            for (i in 3 downTo 1) {
                Toast.makeText(applicationContext, "Odłóż telefon! Zostało ${i * 10} sekund", Toast.LENGTH_LONG).show()
                playWarningBeep()
                delay(10000) // Czekamy 10 sekund do następnego ostrzeżenia
            }
            // Jeśli Coroutine nie zostało zablokowane (telefon się ruszał przez 30 sekund) -> KARA
            abortSessionWithNegativeOpinion("Test Kamienia (Ignorowanie ostrzeżeń)")
        }
    }

    private fun playWarningBeep() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, uri)
            r.play()
        } catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * Terminates the current session completely, marks it as negatively aborted in local history,
     * processes the penalty via OfflineSyncManager, and notifies the UI to update.
     */
    private fun abortSessionWithNegativeOpinion(penaltyType: String) {
        val tokenManager = TokenManager(applicationContext)
        val token = tokenManager.getToken()
        val sessionId = tokenManager.getSessionId()
        val offlineManager = OfflineSyncManager(applicationContext)

        val prefs = applicationContext.getSharedPreferences("sleepguardian_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("negative_session_$sessionId", true).apply()

        if (token != null) {
            CoroutineScope(Dispatchers.IO).launch {
                if (sessionId == -2) {
                    offlineManager.addPenaltyToActiveOfflineSession(penaltyType)
                    offlineManager.endOfflineSessionAndQueue()
                } else if (sessionId > 0) {
                    try {
                        RetrofitClient.apiService.logPenalty("Bearer $token", sessionId, LogPenaltyRequest(penaltyType))
                        RetrofitClient.apiService.endSession("Bearer $token", sessionId)
                    } catch (e: Exception) {
                        offlineManager.queueStandalonePenalty(sessionId, penaltyType)
                        offlineManager.queueStandaloneEnd(sessionId)
                    }
                }
                tokenManager.saveSessionId(-1)
                sendBroadcast(Intent("com.example.sleepguardian.SESSION_ABORTED"))
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        warningJob?.cancel()
        stationaryJob?.cancel()
    }
}