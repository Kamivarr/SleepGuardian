package com.prz.sleepguardian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Foreground service managing the stroboscopic flashlight penalty.
 * Allows user to surrender via the notification tray, applying a penalty and ending the session.
 */
class LighthouseService : Service() {

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    private var isFlashing = false
    private var flashThread: Thread? = null
    private var screenStateReceiver: BroadcastReceiver? = null
    private var graceTimer: CountDownTimer? = null

    private val gracePeriodMs: Long = 120000

    companion object {
        const val ACTION_ABORT_SESSION = "com.example.sleepguardian.ABORT_LIGHTHOUSE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ABORT_SESSION) {
            abortSessionWithNegativeOpinion("Latarnia Morska (Poddanie się)")
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        initializeCamera()
        initializeForegroundService()
        registerScreenStateReceiver()
        startGraceTimer()
    }

    private fun initializeCamera() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initializeForegroundService() {
        val channelId = "sleep_guardian_light_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SleepGuardian Flashlight",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val abortIntent = Intent(this, LighthouseService::class.java).apply {
            action = ACTION_ABORT_SESSION
        }
        val abortPendingIntent = PendingIntent.getService(
            this, 0, abortIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Latarnia Morska jest aktywna")
            .setContentText("Zablokuj ekran, aby zgasić.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Poddaję się", abortPendingIntent)
            .build()

        startForeground(4, notification)
    }

    private fun registerScreenStateReceiver() {
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> startGraceTimer()
                    Intent.ACTION_SCREEN_OFF -> {
                        cancelGraceTimer()
                        stopFlashing()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    private fun startGraceTimer() {
        if (isFlashing) return
        graceTimer?.cancel()
        graceTimer = object : CountDownTimer(gracePeriodMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                startFlashing()
            }
        }.start()
    }

    private fun cancelGraceTimer() {
        graceTimer?.cancel()
        graceTimer = null
    }

    private fun startFlashing() {
        if (isFlashing || cameraId == null) return

        isFlashing = true
        flashThread = Thread {
            var torchState = false
            while (isFlashing) {
                try {
                    torchState = !torchState
                    cameraManager.setTorchMode(cameraId!!, torchState)
                    Thread.sleep(500)
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }
            ensureTorchOff()
        }
        flashThread?.start()
    }

    private fun stopFlashing() {
        isFlashing = false
        flashThread?.join(1000)
        flashThread = null
        ensureTorchOff()
    }

    private fun ensureTorchOff() {
        cameraId?.let {
            try {
                cameraManager.setTorchMode(it, false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun abortSessionWithNegativeOpinion(penaltyType: String) {
        val tokenManager = TokenManager(applicationContext)
        val token = tokenManager.getToken()
        val sessionId = tokenManager.getSessionId()
        val offlineManager = OfflineSyncManager(applicationContext)

        val prefs = applicationContext.getSharedPreferences("sleepguardian_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("negative_session_$sessionId", true).apply()

        offlineManager.recordFailedSession()
        offlineManager.recordSessionEnd()

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

    override fun onDestroy() {
        super.onDestroy()
        cancelGraceTimer()
        stopFlashing()
        screenStateReceiver?.let { unregisterReceiver(it) }
    }
}