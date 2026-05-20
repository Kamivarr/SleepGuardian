package com.example.sleepguardian

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
 * Introduces a 120-second grace period before hardware activation and backend reporting.
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
        const val ACTION_STOP_LIGHTHOUSE = "com.example.sleepguardian.STOP_LIGHTHOUSE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_LIGHTHOUSE) {
            stopSelf()
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

        val stopIntent = Intent(this, LighthouseService::class.java).apply {
            action = ACTION_STOP_LIGHTHOUSE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Latarnia Morska jest aktywna")
            .setContentText("Zablokuj ekran, aby zgasić. Kliknij poniżej, aby zakończyć rygor.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Zakończ sesję", stopPendingIntent)
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
                reportPenalty("Latarnia Morska")
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

    /**
     * Dispatches rule violations to the backend API or caches them locally via OfflineSyncManager.
     * Evaluates active session state explicitly designed for robust offline execution.
     */
    private fun reportPenalty(penaltyType: String) {
        val tokenManager = TokenManager(applicationContext)
        val token = tokenManager.getToken()
        val sessionId = tokenManager.getSessionId()
        val offlineManager = OfflineSyncManager(applicationContext)

        if (token != null) {
            // TODO: Extract common Coroutine exception handling for improved crash reporting
            CoroutineScope(Dispatchers.IO).launch {
                if (sessionId == -2) {
                    // ID -2 defines an offline-native session boundary
                    offlineManager.addPenaltyToActiveOfflineSession(penaltyType)
                } else if (sessionId > 0) {
                    // Standard synced session execution
                    try {
                        val request = LogPenaltyRequest(penaltyType)
                        RetrofitClient.apiService.logPenalty("Bearer $token", sessionId, request)
                    } catch (e: Exception) {
                        // Execution faltered during network disruption - queue for morning sync
                        offlineManager.queueStandalonePenalty(sessionId, penaltyType)
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelGraceTimer()
        stopFlashing()
        screenStateReceiver?.let { unregisterReceiver(it) }
    }
}