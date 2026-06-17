package com.prz.sleepguardian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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

/**
 * Foreground service for the "Test Kamienia" penalty mode.
 * Evaluates planar orientation to ensure the device is completely flat.
 * Aborts session automatically after ignoring consecutive warnings.
 */
class StoneTestService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var warningJob: Job? = null
    private var stationaryJob: Job? = null

    companion object {
        const val ACTION_ABORT_SESSION = "com.example.sleepguardian.ABORT_STONE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ABORT_SESSION) {
            abortSessionWithNegativeOpinion("Test Kamienia (Poddanie się)")
            return START_NOT_STICKY
        }
        return START_STICKY
    }

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

        val abortIntent = Intent(this, StoneTestService::class.java).apply {
            action = ACTION_ABORT_SESSION
        }
        val abortPendingIntent = PendingIntent.getService(
            this, 0, abortIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Test Kamienia")
            .setContentText("Połóż telefon płasko. Każde podniesienie wywołuje karę.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Poddaję się", abortPendingIntent)
            .build()

        startForeground(2, notification)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            // Urządzenie leżące płasko na stole ma wektory X i Y bliskie 0, a Z bliskie grawitacji (9.81)
            // Znaczne wychylenie osi X lub Y wskazuje na to, że telefon jest w dłoni.
            val isFlat = Math.abs(x) < 2.0f && Math.abs(y) < 2.0f && Math.abs(z) > 8.0f
            val isMoving = !isFlat

            if (isMoving) {
                stationaryJob?.cancel()
                stationaryJob = null

                if (warningJob == null) {
                    startWarningSequence()
                }
            } else {
                if (warningJob != null && stationaryJob == null) {
                    stationaryJob = CoroutineScope(Dispatchers.Default).launch {
                        delay(2000)
                        warningJob?.cancel()
                        warningJob = null
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Telefon odłożony płasko. Reset licznika.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * Executes the 30-second penalty verification pipeline.
     */
    private fun startWarningSequence() {
        warningJob = CoroutineScope(Dispatchers.Main).launch {
            for (i in 3 downTo 1) {
                Toast.makeText(applicationContext, "Odłóż telefon płasko! Zostało ${i * 10} sekund", Toast.LENGTH_LONG).show()
                playWarningBeep()
                delay(10000)
            }
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

    private fun abortSessionWithNegativeOpinion(penaltyType: String) {
        val tokenManager = TokenManager(applicationContext)
        val token = tokenManager.getToken()
        val sessionId = tokenManager.getSessionId()
        val offlineManager = OfflineSyncManager(applicationContext)

        val prefs = applicationContext.getSharedPreferences("sleepguardian_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("negative_session_$sessionId", true).apply()

        offlineManager.recordFailedSession()
        offlineManager.recordSessionEnd() // Zablokuj farmienie

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

                // SetPackage gwarantuje, że najnowszy Android 14+ przepuści nasz Broadcast!
                val intent = Intent("com.example.sleepguardian.SESSION_ABORTED").apply {
                    setPackage(applicationContext.packageName)
                }
                sendBroadcast(intent)
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