package com.example.sleepguardian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service managing the audio penalty.
 * Allows user to surrender via the notification tray, applying a penalty and ending the session.
 */
class MosquitoService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var screenStateReceiver: BroadcastReceiver? = null
    private var graceTimer: CountDownTimer? = null

    private val gracePeriodMs: Long = 120000

    companion object {
        const val ACTION_ABORT_SESSION = "com.example.sleepguardian.ABORT_MOSQUITO"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ABORT_SESSION) {
            abortSessionWithNegativeOpinion("Upierdliwy Komar (Poddanie się)")
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        initializeForegroundService()
        registerScreenStateReceiver()
        startGraceTimer()
    }

    private fun initializeForegroundService() {
        val channelId = "sleep_guardian_audio_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SleepGuardian Audio",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val abortIntent = Intent(this, MosquitoService::class.java).apply {
            action = ACTION_ABORT_SESSION
        }
        val abortPendingIntent = PendingIntent.getService(
            this,
            0,
            abortIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ostrzeżenie Dźwiękowe")
            .setContentText("Zablokuj ekran, aby wyciszyć.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Poddaję się",
                abortPendingIntent
            )
            .build()

        startForeground(3, notification)
    }

    private fun registerScreenStateReceiver() {
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> startGraceTimer()
                    Intent.ACTION_SCREEN_OFF -> {
                        cancelGraceTimer()
                        stopMosquitoSound()
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
        if (isPlaying) return

        graceTimer?.cancel()
        graceTimer = object : CountDownTimer(gracePeriodMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) = Unit
            override fun onFinish() {
                startMosquitoSound()
            }
        }.start()
    }

    private fun cancelGraceTimer() {
        graceTimer?.cancel()
        graceTimer = null
    }

    private fun startMosquitoSound() {
        if (isPlaying) return

        try {
            stopMosquitoSound()

            val afd = resources.openRawResourceFd(R.raw.mosquito) ?: return

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )

                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }

            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
            stopMosquitoSound()
        }
    }

    private fun stopMosquitoSound() {
        isPlaying = false

        mediaPlayer?.run {
            try {
                if (this.isPlaying) {
                    stop()
                }
            } catch (_: IllegalStateException) {
            } finally {
                release()
            }
        }

        mediaPlayer = null
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
                        RetrofitClient.apiService.logPenalty(
                            "Bearer $token",
                            sessionId,
                            LogPenaltyRequest(penaltyType)
                        )
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
        stopMosquitoSound()
        screenStateReceiver?.let { unregisterReceiver(it) }
    }
}