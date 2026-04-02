package com.example.poketmon_on_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.poketmon_on_app.R
import com.example.poketmon_on_app.pet.PetPreferences
import com.example.poketmon_on_app.pet.PetState
import com.example.poketmon_on_app.pet.PetStateMachine
import com.example.poketmon_on_app.pet.PokemonRepository
import com.example.poketmon_on_app.pet.SpriteSheet
import com.example.poketmon_on_app.ui.PetView
import kotlin.concurrent.thread

class PetOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "pokepet_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_CHANGE_POKEMON = "com.example.poketmon_on_app.CHANGE_POKEMON"
        const val ACTION_UPDATE_SETTINGS = "com.example.poketmon_on_app.UPDATE_SETTINGS"
        const val ACTION_COMMAND = "com.example.poketmon_on_app.COMMAND"
        const val EXTRA_POKEMON_ID = "pokemon_id"
        const val EXTRA_COMMAND = "command"

        // Broadcast from service → activity
        const val BROADCAST_STATE = "com.example.poketmon_on_app.STATE_CHANGED"
        const val EXTRA_STATE = "state"
        const val ACTION_STOP = "com.example.poketmon_on_app.STOP"
        const val ACTION_TOGGLE_SLEEP = "com.example.poketmon_on_app.TOGGLE_SLEEP"
        private const val LONG_PRESS_MS = 500L
        private const val TAP_THRESHOLD = 10f
    }

    private var currentPokemonName: String = "포켓몬"

    private lateinit var windowManager: WindowManager
    private lateinit var preferences: PetPreferences
    private var petView: PetView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var stateMachine: PetStateMachine? = null
    private var currentSheet: SpriteSheet? = null
    private var currentAnimName: String = "Idle"

    // Menu
    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null

    // Touch state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var touchDownTime = 0L
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false

    // Screen bounds
    private var screenWidth = 0
    private var screenHeight = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateIntervalMs = 50L // ~20fps — sufficient for overlay movement, avoids System UI overload

    // Screen state receiver
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    petView?.stopAnimation()
                    mainHandler.removeCallbacks(gameLoop)
                }
                Intent.ACTION_SCREEN_ON -> {
                    petView?.startAnimation()
                    mainHandler.post(gameLoop)
                    // Lock screen: disable touch
                    layoutParams?.let { params ->
                        params.flags = params.flags or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        petView?.let { windowManager.updateViewLayout(it, params) }
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    // Unlocked: enable touch
                    layoutParams?.let { params ->
                        params.flags = params.flags and
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        petView?.let { windowManager.updateViewLayout(it, params) }
                    }
                }
            }
        }
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            val sm = stateMachine
            sm?.update()
            if (sm?.currentState == PetState.WALK || sm?.currentState == PetState.RUN) {
                moveOverlay()
                mainHandler.postDelayed(this, updateIntervalMs)
            } else {
                // IDLE/SLEEP/REACTION — no movement needed, poll less frequently
                mainHandler.postDelayed(this, 500L)
            }
        }
    }

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        showMenu()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferences = PetPreferences(this)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        preferences.isServiceRunning = true

        // Register screen state receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CHANGE_POKEMON -> {
                val pokemonId = intent.getIntExtra(EXTRA_POKEMON_ID, -1)
                if (pokemonId > 0) {
                    loadPokemon(pokemonId)
                }
            }
            ACTION_UPDATE_SETTINGS -> applySettings()
            ACTION_COMMAND -> {
                when (intent.getStringExtra(EXTRA_COMMAND)) {
                    "sleep" -> stateMachine?.transitionTo(PetState.SLEEP)
                    "wake" -> stateMachine?.transitionTo(PetState.IDLE)
                    "walk" -> stateMachine?.transitionTo(PetState.WALK)
                    "run" -> stateMachine?.transitionTo(PetState.RUN)
                }
            }
            ACTION_STOP -> stopSelf()
            ACTION_TOGGLE_SLEEP -> {
                val sm = stateMachine
                if (sm?.currentState == PetState.SLEEP) {
                    sm.transitionTo(PetState.IDLE)
                } else {
                    sm?.transitionTo(PetState.SLEEP)
                }
            }
        }
        return START_STICKY
    }

    private fun applySettings() {
        val opacity = preferences.opacity / 100f

        // Resize overlay using sprite scale formula
        resizeOverlay()

        // Update opacity
        petView?.alpha = opacity

        // Update state machine params
        stateMachine?.apply {
            baseSpeed = preferences.getMoveSpeedPx()
            activityLevel = preferences.activityLevel
            sleepTimeoutMs = preferences.sleepTimeout * 60 * 1000L
        }
    }

    private fun getSpriteScale(): Double {
        val baseScale = screenHeight / 450.0
        val userScale = preferences.scale / 100.0
        return baseScale * userScale
    }

    /**
     * Resize overlay to match the current animation's frame size × spriteScale.
     * Maintains bottom-center anchor so feet stay in place.
     */
    private fun resizeForAnimation(animName: String) {
        val sheet = currentSheet ?: return
        val params = layoutParams ?: return
        val view = petView ?: return
        val info = sheet.anims[animName] ?: return

        val spriteScale = getSpriteScale()
        val newWidth = (info.frameWidth * spriteScale).toInt().coerceAtLeast(24)
        val newHeight = (info.frameHeight * spriteScale).toInt().coerceAtLeast(24)

        // Bottom-center anchor: keep feet position fixed
        val oldCenterX = params.x + params.width / 2
        val oldBottomY = params.y + params.height

        params.width = newWidth
        params.height = newHeight
        params.x = oldCenterX - newWidth / 2
        params.y = oldBottomY - newHeight

        // Keep within screen bounds
        if (params.x + newWidth > screenWidth) params.x = screenWidth - newWidth
        if (params.y + newHeight > screenHeight) params.y = screenHeight - newHeight
        if (params.x < 0) params.x = 0
        if (params.y < 0) params.y = 0

        currentAnimName = animName

        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    private fun resizeOverlay() {
        resizeForAnimation(currentAnimName)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(gameLoop)
        longPressHandler.removeCallbacks(longPressRunnable)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        dismissMenu()
        petView?.stopAnimation()
        petView?.let { windowManager.removeView(it) }
        petView = null
        stateMachine = null
        preferences.isServiceRunning = false
        super.onDestroy()
    }

    private fun showOverlay() {
        val view = PetView(this)

        val initialSize = 200 // placeholder — resized in loadPokemon → applySettings
        val params = WindowManager.LayoutParams(
            initialSize,
            initialSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
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
                    touchDownTime = System.currentTimeMillis()
                    longPressTriggered = false
                    longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    dismissMenu()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val movedX = Math.abs(event.rawX - initialTouchX)
                    val movedY = Math.abs(event.rawY - initialTouchY)
                    if (movedX > TAP_THRESHOLD || movedY > TAP_THRESHOLD) {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        if (!longPressTriggered) {
                            stateMachine?.transitionTo(PetState.DRAGGED)
                        }
                    }
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(v, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    val movedX = Math.abs(event.rawX - initialTouchX)
                    val movedY = Math.abs(event.rawY - initialTouchY)
                    if (!longPressTriggered && movedX < TAP_THRESHOLD && movedY < TAP_THRESHOLD) {
                        handleTap()
                    } else if (!longPressTriggered) {
                        stateMachine?.transitionTo(PetState.IDLE)
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, params)
        petView = view
        layoutParams = params

        loadPokemon(preferences.selectedPokemonId)
    }

    private fun handleTap() {
        val sm = stateMachine ?: return
        val sheet = currentSheet ?: return

        if (sm.currentState == PetState.SLEEP) {
            sm.transitionTo(PetState.IDLE)
            return
        }

        if (sm.currentState == PetState.REACTION || sm.currentState == PetState.DRAGGED) return

        val reactions = sheet.availableReactions()
        if (reactions.isEmpty()) {
            sm.transitionTo(PetState.IDLE)
            return
        }

        val reactionAnim = reactions.random()
        sm.transitionTo(PetState.REACTION)

        petView?.apply {
            setAnimation(reactionAnim)
            speedMultiplier = 1.0f
        }
        resizeForAnimation(reactionAnim)

        // Return to idle after animation plays once
        val info = sheet.anims[reactionAnim]
        if (info != null) {
            val totalMs = info.durations.sumOf { it } * 1000L / 60L
            mainHandler.postDelayed({
                if (sm.currentState == PetState.REACTION) {
                    sm.transitionTo(PetState.IDLE)
                }
            }, totalMs.coerceAtLeast(500L))
        } else {
            mainHandler.postDelayed({
                if (sm.currentState == PetState.REACTION) {
                    sm.transitionTo(PetState.IDLE)
                }
            }, 1000L)
        }
    }

    // ---- Long press menu ----

    private fun showMenu() {
        dismissMenu()
        val params = layoutParams ?: return

        val inflater = LayoutInflater.from(this)
        val menu = inflater.inflate(R.layout.overlay_menu, null)

        val sm = stateMachine
        val sleepWake = menu.findViewById<TextView>(R.id.menuSleepWake)
        val walkRun = menu.findViewById<TextView>(R.id.menuWalkRun)
        val stop = menu.findViewById<TextView>(R.id.menuStop)

        if (sm?.currentState == PetState.SLEEP) {
            sleepWake.text = "깨우기"
        } else {
            sleepWake.text = "재우기"
        }

        if (sm?.currentState == PetState.RUN) {
            walkRun.text = "걷기"
        } else {
            walkRun.text = "뛰기"
        }

        // Disable walk if no Walk animation
        if (currentSheet?.resolveAnimation("Walk") == null) {
            walkRun.alpha = 0.4f
            walkRun.isEnabled = false
        }

        sleepWake.setOnClickListener {
            if (sm?.currentState == PetState.SLEEP) {
                sm.transitionTo(PetState.IDLE)
            } else {
                sm?.transitionTo(PetState.SLEEP)
            }
            dismissMenu()
        }

        walkRun.setOnClickListener {
            if (sm?.currentState == PetState.RUN) {
                sm.transitionTo(PetState.WALK)
            } else {
                sm?.transitionTo(PetState.RUN)
            }
            dismissMenu()
        }

        stop.setOnClickListener {
            dismissMenu()
            stopSelf()
        }

        val menuP = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = params.x
            y = params.y - params.height
        }

        windowManager.addView(menu, menuP)
        menuView = menu
        menuParams = menuP

        // Auto-dismiss after 5 seconds
        mainHandler.postDelayed({ dismissMenu() }, 5000L)
    }

    private fun dismissMenu() {
        menuView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        menuView = null
        menuParams = null
    }

    // ---- Movement ----

    private fun moveOverlay() {
        val sm = stateMachine ?: return
        val params = layoutParams ?: return
        val view = petView ?: return

        if (sm.currentState != PetState.WALK && sm.currentState != PetState.RUN) return

        params.x += sm.dx.toInt()
        params.y += sm.dy.toInt()

        val viewSize = params.width
        if (params.x < 0) {
            params.x = 0
            sm.bounceX()
        } else if (params.x + viewSize > screenWidth) {
            params.x = screenWidth - viewSize
            sm.bounceX()
        }
        if (params.y < 0) {
            params.y = 0
            sm.bounceY()
        } else if (params.y + viewSize > screenHeight) {
            params.y = screenHeight - viewSize
            sm.bounceY()
        }

        view.setDirection(sm.direction.row)

        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    // ---- Pokemon loading ----

    private val pokemonRepository: PokemonRepository by lazy { PokemonRepository(this) }

    private fun loadPokemon(pokemonId: Int) {
        mainHandler.removeCallbacks(gameLoop)
        currentPokemonName = pokemonRepository.getAll().find { it.id == pokemonId }?.name ?: "포켓몬"
        thread {
            val sheet = SpriteSheet(this, pokemonId)
            mainHandler.post {
                currentSheet = sheet
                val sm = PetStateMachine(sheet) { state, animName ->
                    petView?.apply {
                        setAnimation(animName)
                        speedMultiplier = stateMachine?.getSpeedMultiplier() ?: 1.0f
                    }
                    resizeForAnimation(animName)
                    broadcastState(state)
                }
                stateMachine = sm

                val idleAnim = sheet.resolveAnimation("Idle") ?: "Idle"
                petView?.apply {
                    setSpriteSheet(sheet)
                    setAnimation(idleAnim)
                    setDirection(0)
                    startAnimation()
                }

                sm.transitionTo(PetState.IDLE)
                applySettings()
                mainHandler.post(gameLoop)
            }
        }
    }

    private fun broadcastState(state: PetState) {
        sendBroadcast(Intent(BROADCAST_STATE).apply {
            putExtra(EXTRA_STATE, state.name)
            setPackage(packageName)
        })
        updateNotification(state)
    }

    // ---- Notification ----

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
        return buildNotificationWithState(PetState.IDLE)
    }

    private fun buildNotificationWithState(state: PetState): Notification {
        val stateLabel = when (state) {
            PetState.IDLE -> "대기 중"
            PetState.WALK -> "걷는 중"
            PetState.RUN -> "뛰는 중"
            PetState.SLEEP -> "자는 중"
            PetState.REACTION -> "반응 중"
            PetState.DRAGGED -> "드래그 중"
        }

        val sleepLabel = if (state == PetState.SLEEP) "깨우기" else "재우기"

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, PetOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val sleepIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PetOverlayService::class.java).apply { action = ACTION_TOGGLE_SLEEP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("$currentPokemonName - $stateLabel")
            .setContentText("화면에서 놀고 있습니다!")
            .setSmallIcon(R.mipmap.ic_launcher)
            .addAction(Notification.Action.Builder(null, sleepLabel, sleepIntent).build())
            .addAction(Notification.Action.Builder(null, "중지", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(state: PetState) {
        val notification = buildNotificationWithState(state)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
