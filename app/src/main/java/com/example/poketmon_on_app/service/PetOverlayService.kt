package com.example.poketmon_on_app.service

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
import android.view.View
import android.view.WindowManager
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

        const val BROADCAST_STATE = "com.example.poketmon_on_app.STATE_CHANGED"
        const val EXTRA_STATE = "state"
        const val ACTION_STOP = "com.example.poketmon_on_app.STOP"
        const val ACTION_TOGGLE_SLEEP = "com.example.poketmon_on_app.TOGGLE_SLEEP"
    }

    private var currentPokemonName: String = "포켓몬"

    private lateinit var windowManager: WindowManager
    private lateinit var preferences: PetPreferences
    private lateinit var notificationManager: PetNotificationManager
    private lateinit var gameDetector: GameDetector
    private lateinit var menuManager: OverlayMenuManager
    private lateinit var touchHandler: PetTouchHandler

    private var petView: PetView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var stateMachine: PetStateMachine? = null
    private var currentSheet: SpriteSheet? = null

    private var screenWidth = 0
    private var screenHeight = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateIntervalMs = 50L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    petView?.stopAnimation()
                    mainHandler.removeCallbacks(gameLoop)
                    gameDetector.stop()
                }
                Intent.ACTION_SCREEN_ON -> {
                    petView?.startAnimation()
                    mainHandler.post(gameLoop)
                    gameDetector.startIfEnabled(preferences.hideInGame)
                    layoutParams?.let { params ->
                        params.flags = params.flags or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        petView?.let { windowManager.updateViewLayout(it, params) }
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
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
                mainHandler.postDelayed(this, 500L)
            }
        }
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

        notificationManager = PetNotificationManager(this, CHANNEL_ID, NOTIFICATION_ID)
        notificationManager.createChannel()
        startForeground(NOTIFICATION_ID, notificationManager.build(currentPokemonName, PetState.IDLE))
        preferences.isServiceRunning = true

        menuManager = OverlayMenuManager(this, windowManager, mainHandler)

        gameDetector = GameDetector(this, mainHandler,
            onHide = {
                petView?.apply {
                    stopAnimation()
                    visibility = View.GONE
                }
                mainHandler.removeCallbacks(gameLoop)
            },
            onRestore = {
                petView?.apply {
                    visibility = View.VISIBLE
                    startAnimation()
                }
                mainHandler.post(gameLoop)
            }
        )

        touchHandler = PetTouchHandler()
        touchHandler.callback = object : PetTouchHandler.Callback {
            override fun getCurrentState() = stateMachine?.currentState
            override fun getLayoutParams() = layoutParams
            override fun onTap() = handleTap()
            override fun onLongPress() = showMenu()
            override fun onDragStart() { stateMachine?.transitionTo(PetState.DRAGGED) }
            override fun onDragEnd() { stateMachine?.transitionTo(PetState.IDLE) }
            override fun dismissMenu() = menuManager.dismiss()
            override fun updateViewLayout(view: View) {
                try { windowManager.updateViewLayout(view, layoutParams) } catch (_: Exception) {}
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        gameDetector.startIfEnabled(preferences.hideInGame)
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CHANGE_POKEMON -> {
                val pokemonId = intent.getIntExtra(EXTRA_POKEMON_ID, -1)
                if (pokemonId > 0) loadPokemon(pokemonId)
            }
            ACTION_UPDATE_SETTINGS -> {
                applySettings()
                gameDetector.startIfEnabled(preferences.hideInGame)
            }
            ACTION_COMMAND -> {
                when (intent.getStringExtra(EXTRA_COMMAND)) {
                    "sleep" -> stateMachine?.transitionTo(PetState.SLEEP)
                    "wake" -> stateMachine?.transitionTo(PetState.IDLE)
                    "walk" -> stateMachine?.transitionTo(PetState.WALK)
                    "run" -> stateMachine?.transitionTo(PetState.RUN)
                    "react" -> handleTap()
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

    override fun onDestroy() {
        mainHandler.removeCallbacks(gameLoop)
        gameDetector.destroy()
        touchHandler.destroy()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        menuManager.dismiss()
        petView?.stopAnimation()
        petView?.let { windowManager.removeView(it) }
        petView = null
        stateMachine = null
        preferences.isServiceRunning = false
        super.onDestroy()
    }

    // ---- Overlay ----

    private fun showOverlay() {
        val view = PetView(this)

        val initialSize = 200
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

        view.setOnTouchListener(touchHandler)

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
            playAnimationOnce(reactionAnim) {
                if (sm.currentState == PetState.REACTION) {
                    sm.transitionTo(PetState.IDLE)
                }
            }
            speedMultiplier = 1.0f
        }
    }

    private fun showMenu() {
        val params = layoutParams ?: return
        menuManager.show(params, stateMachine?.currentState, currentSheet?.resolveAnimation("Walk") != null,
            object : OverlayMenuManager.Callback {
                override fun onSleepWake() {
                    val sm = stateMachine ?: return
                    if (sm.currentState == PetState.SLEEP) sm.transitionTo(PetState.IDLE)
                    else sm.transitionTo(PetState.SLEEP)
                }
                override fun onWalkRun() {
                    val sm = stateMachine ?: return
                    if (sm.currentState == PetState.RUN) sm.transitionTo(PetState.WALK)
                    else sm.transitionTo(PetState.RUN)
                }
                override fun onStop() { stopSelf() }
            }
        )
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

    // ---- Settings & Resize ----

    private fun applySettings() {
        resizeOverlayToMax()
        petView?.alpha = preferences.opacity / 100f
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
     * 오버레이를 최대 프레임 크기 기준으로 리사이즈.
     * 애니메이션 전환 시에는 호출하지 않음 — surface 재할당으로 인한 끊김 방지.
     * 설정 변경이나 포켓몬 로드 시에만 호출.
     */
    private fun resizeOverlayToMax() {
        val sheet = currentSheet ?: return
        val params = layoutParams ?: return
        val view = petView ?: return

        val spriteScale = getSpriteScale()
        val newWidth = (sheet.maxFrameWidth * spriteScale).toInt().coerceAtLeast(24)
        val newHeight = (sheet.maxFrameHeight * spriteScale).toInt().coerceAtLeast(24)

        if (params.width == newWidth && params.height == newHeight) return

        val oldCenterX = params.x + params.width / 2
        val oldBottomY = params.y + params.height

        params.width = newWidth
        params.height = newHeight
        params.x = oldCenterX - newWidth / 2
        params.y = oldBottomY - newHeight

        if (params.x + newWidth > screenWidth) params.x = screenWidth - newWidth
        if (params.y + newHeight > screenHeight) params.y = screenHeight - newHeight
        if (params.x < 0) params.x = 0
        if (params.y < 0) params.y = 0

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
                    broadcastState(state)
                }
                stateMachine = sm

                val idleAnim = sheet.resolveAnimation("Idle") ?: "Idle"
                petView?.apply {
                    maxFrameWidth = sheet.maxFrameWidth
                    maxFrameHeight = sheet.maxFrameHeight
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
        notificationManager.update(currentPokemonName, state)
    }
}
