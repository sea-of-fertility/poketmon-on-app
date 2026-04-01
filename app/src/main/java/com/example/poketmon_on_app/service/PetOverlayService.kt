package com.example.poketmon_on_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import com.example.poketmon_on_app.R
import com.example.poketmon_on_app.pet.SpriteSheet
import com.example.poketmon_on_app.ui.PetView

class PetOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "pokepet_channel"
        const val NOTIFICATION_ID = 1
        const val DEFAULT_POKEMON_ID = 25 // Pikachu
    }

    private lateinit var windowManager: WindowManager
    private var petView: PetView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // Drag state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
    }

    override fun onDestroy() {
        petView?.stopAnimation()
        petView?.let { windowManager.removeView(it) }
        petView = null
        super.onDestroy()
    }

    private fun showOverlay() {
        val spriteSheet = SpriteSheet(this, DEFAULT_POKEMON_ID)
        val view = PetView(this).apply {
            setSpriteSheet(spriteSheet)
            setAnimation("Idle")
            setDirection(0)
        }

        val size = 200
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 300
            y = 500
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(v, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.performClick()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, params)
        view.startAnimation()

        petView = view
        layoutParams = params
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PokePet",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "PokePet overlay service"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PokePet")
            .setContentText("Pikachu is on your screen!")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }
}
