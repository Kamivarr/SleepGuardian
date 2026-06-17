package com.prz.sleepguardian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Foreground service responsible for rendering the system-level overlay (Curtain).
 * Forces users to surrender via "Poddaję się" to regain device control, resulting in an immediate session abort.
 */
class CurtainService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: LinearLayout? = null
    private var screenStateReceiver: BroadcastReceiver? = null
    private var graceTimer: CountDownTimer? = null

    private val gracePeriodMs: Long = 120000
    private var isOverlayShowing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initializeForegroundService()
        registerScreenStateReceiver()
        startGraceTimer()
    }

    private fun initializeForegroundService() {
        val channelId = "sleep_guardian_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SleepGuardian Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SleepGuardian")
            .setContentText("Ochrona snu aktywna")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    private fun registerScreenStateReceiver() {
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> startGraceTimer()
                    Intent.ACTION_SCREEN_OFF -> {
                        cancelGraceTimer()
                        removeOverlay()
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
        if (isOverlayShowing) return
        graceTimer?.cancel()
        graceTimer = object : CountDownTimer(gracePeriodMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() { renderOverlay() }
        }.start()
    }

    private fun cancelGraceTimer() {
        graceTimer?.cancel()
        graceTimer = null
    }

    private fun renderOverlay() {
        if (isOverlayShowing) return
        isOverlayShowing = true

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F2080808"))
            setPadding(64, 64, 64, 64)
            this.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val titleText = TextView(this).apply {
            text = "ZASŁONA SNU"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }

        val subtitleText = TextView(this).apply {
            text = "Twój ekran został zablokowany, aby chronić Twój sen.\nOdkładając telefon, dbasz o swój streak."
            setTextColor(Color.parseColor("#B3FFFFFF"))
            textSize = 16f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 128)
        }

        val unlockButton = Button(this).apply {
            text = "PODDAJĘ SIĘ, JESTEM MIĘKKI JAK MIĘCZAK"
            setTextColor(Color.parseColor("#FF5252"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 50f
                setStroke(3, Color.parseColor("#FF5252"))
                setColor(Color.parseColor("#1AFFFFFF"))
            }
            setPadding(64, 32, 64, 32)
            setOnClickListener {
                abortSessionWithNegativeOpinion("Narastająca Kurtyna (Poddanie się)")
            }
        }

        overlayView?.addView(titleText)
        overlayView?.addView(subtitleText)
        overlayView?.addView(unlockButton)
        windowManager.addView(overlayView, layoutParams)
    }

    private fun removeOverlay() {
        if (!isOverlayShowing) return
        isOverlayShowing = false
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
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
        removeOverlay()
        screenStateReceiver?.let { unregisterReceiver(it) }
    }
}