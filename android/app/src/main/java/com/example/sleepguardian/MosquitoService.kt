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
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.sin

/**
 * Foreground service managing the high-frequency audio penalty.
 * Toggles audio playback based on screen hardware states and provides a notification action to terminate.
 */
class MosquitoService : Service() {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var audioThread: Thread? = null
    private var screenStateReceiver: BroadcastReceiver? = null

    private val sampleRate = 44100
    private val frequency = 9000.0
    private val durationInSeconds = 1
    private val volumeFactor = 0.1

    companion object {
        const val ACTION_STOP_MOSQUITO = "com.example.sleepguardian.STOP_MOSQUITO"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_MOSQUITO) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        initializeForegroundService()
        registerScreenStateReceiver()
        startMosquitoSound() // Active by default since the screen is currently ON
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

        // Intent for the notification action button to terminate the session
        val stopIntent = Intent(this, MosquitoService::class.java).apply {
            action = ACTION_STOP_MOSQUITO
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Upierdliwy Komar jest aktywny")
            .setContentText("Zablokuj ekran, aby wyciszyć. Kliknij poniżej, aby zakończyć rygor.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Zakończ sesję", stopPendingIntent)
            .build()

        startForeground(3, notification)
    }

    private fun registerScreenStateReceiver() {
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> startMosquitoSound()
                    Intent.ACTION_SCREEN_OFF -> stopMosquitoSound()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    private fun startMosquitoSound() {
        if (isPlaying) return

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        isPlaying = true
        audioTrack?.play()

        audioThread = Thread {
            val numSamples = durationInSeconds * sampleRate
            val generatedSnd = ShortArray(numSamples)

            while (isPlaying) {
                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i.toDouble() / (sampleRate / frequency)
                    generatedSnd[i] = (sin(angle) * Short.MAX_VALUE * volumeFactor).toInt().toShort()
                }
                audioTrack?.write(generatedSnd, 0, numSamples)
            }
        }
        audioThread?.start()
    }

    private fun stopMosquitoSound() {
        isPlaying = false
        audioThread?.join(500)
        audioThread = null

        audioTrack?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        audioTrack = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMosquitoSound()
        screenStateReceiver?.let { unregisterReceiver(it) }
    }
}