package com.leoxs.fpscounter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private lateinit var overlayParams: WindowManager.LayoutParams
    private var frameCallback: Choreographer.FrameCallback? = null

    private var frameCount = 0
    private var lastReportTimeNanos = 0L

    // Geçmiş kaydı için oturum istatistikleri
    private var sessionStartTimeMillis = 0L
    private var fpsSum = 0L
    private var fpsSampleCount = 0
    private var fpsMin = Int.MAX_VALUE
    private var fpsMax = 0

    companion object {
        private const val CHANNEL_ID = "fps_overlay_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        addOverlay()
        startFpsCounter()
        sessionStartTimeMillis = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Uygulama "son uygulamalar"dan kapatılsa bile servis çalışmaya devam etsin diye
        // burada bilerek hiçbir şey yapmıyoruz (servisi durdurmuyoruz).
        // Kullanıcı isterse bildirimden veya uygulama içinden "Durdur" ile kapatabilir.
    }

    override fun onDestroy() {
        super.onDestroy()
        saveSessionToHistory()
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // view zaten kaldırılmış olabilir
            }
        }
    }

    private fun saveSessionToHistory() {
        if (fpsSampleCount == 0) return
        val avg = (fpsSum / fpsSampleCount).toInt()
        val durationSeconds = (System.currentTimeMillis() - sessionStartTimeMillis) / 1000
        FpsLogManager.appendSession(
            this,
            avg = avg,
            min = if (fpsMin == Int.MAX_VALUE) 0 else fpsMin,
            max = fpsMax,
            durationSeconds = durationSeconds
        )
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FPS Counter",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FPS Counter çalışıyor")
            .setContentText("Ekranda FPS göstergesi aktif")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun addOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val webView = WebView(this)
        webView.setBackgroundColor(0) // şeffaf arkaplan
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("file:///android_asset/overlay.html")
        overlayView = webView

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        overlayParams.gravity = Gravity.TOP or Gravity.START
        overlayParams.x = 20
        overlayParams.y = 100

        setupDragging(webView)

        windowManager?.addView(overlayView, overlayParams)
    }

    /**
     * Göstergeye dokunup sürükleyerek ekranda istenilen yere taşımayı sağlar.
     * FLAG_NOT_TOUCH_MODAL sayesinde göstergenin dışındaki her dokunma hâlâ
     * oyuna geçiyor; sadece göstergenin üzerine basılan dokunmalar sürükleme
     * için bu pencereye yönleniyor.
     */
    private fun setupDragging(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = overlayParams.x
                    initialY = overlayParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    overlayParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    overlayParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, overlayParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun startFpsCounter() {
        lastReportTimeNanos = System.nanoTime()

        frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameCount++
                val elapsedNanos = frameTimeNanos - lastReportTimeNanos

                if (elapsedNanos >= 1_000_000_000L) {
                    val fps = (frameCount * 1_000_000_000.0 / elapsedNanos).toInt()
                    updateOverlayFps(fps)
                    recordFpsSample(fps)
                    frameCount = 0
                    lastReportTimeNanos = frameTimeNanos
                }

                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(frameCallback!!)
    }

    private fun recordFpsSample(fps: Int) {
        fpsSum += fps
        fpsSampleCount++
        if (fps < fpsMin) fpsMin = fps
        if (fps > fpsMax) fpsMax = fps
    }

    private fun updateOverlayFps(fps: Int) {
        overlayView?.evaluateJavascript("updateFPS($fps);", null)
    }
}
