package com.example.sleepguardian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
 * Reports penalty to the backend if the user manually overrides the restriction.
 */
class CurtainService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initializeForegroundService()
        renderOverlay()
    }

    private fun initializeForegroundService() {
        val channelId = "sleep_guardian_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SleepGuardian Service",
                NotificationManager.IMPORTANCE_LOW
            )
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

    private fun renderOverlay() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F2080808")) // 95% opacity dark surface
            setPadding(64, 64, 64, 64)
            this.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
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
            setTextColor(Color.parseColor("#B3FFFFFF")) // 70% opacity white
            textSize = 16f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 128)
        }

        val unlockButton = Button(this).apply {
            text = "AWARYJNE ODBLOKOWANIE (KOSZTUJE SERCE)"
            setTextColor(Color.parseColor("#FF5252"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 50f
                setStroke(3, Color.parseColor("#FF5252"))
                setColor(Color.parseColor("#1AFFFFFF"))
            }
            setPadding(64, 32, 64, 32)
            setOnClickListener {
                reportPenalty("Narastająca Kurtyna (Przerwanie)")
                stopSelf()
            }
        }

        overlayView?.addView(titleText)
        overlayView?.addView(subtitleText)
        overlayView?.addView(unlockButton)
        windowManager.addView(overlayView, layoutParams)
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

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}