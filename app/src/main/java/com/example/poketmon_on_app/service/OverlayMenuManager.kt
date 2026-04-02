package com.example.poketmon_on_app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.poketmon_on_app.R
import com.example.poketmon_on_app.pet.PetState

class OverlayMenuManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val mainHandler: Handler
) {

    interface Callback {
        fun onSleepWake()
        fun onWalkRun()
        fun onStop()
    }

    private var menuView: View? = null

    fun show(
        anchorParams: WindowManager.LayoutParams,
        state: PetState?,
        hasWalkAnim: Boolean,
        callback: Callback
    ) {
        dismiss()

        val menu = LayoutInflater.from(context).inflate(R.layout.overlay_menu, null)

        val sleepWake = menu.findViewById<TextView>(R.id.menuSleepWake)
        val walkRun = menu.findViewById<TextView>(R.id.menuWalkRun)
        val stop = menu.findViewById<TextView>(R.id.menuStop)

        sleepWake.text = if (state == PetState.SLEEP) "깨우기" else "재우기"
        walkRun.text = if (state == PetState.RUN) "걷기" else "뛰기"

        if (!hasWalkAnim) {
            walkRun.alpha = 0.4f
            walkRun.isEnabled = false
        }

        sleepWake.setOnClickListener {
            callback.onSleepWake()
            dismiss()
        }

        walkRun.setOnClickListener {
            callback.onWalkRun()
            dismiss()
        }

        stop.setOnClickListener {
            dismiss()
            callback.onStop()
        }

        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = anchorParams.x
            y = anchorParams.y - anchorParams.height
        }

        windowManager.addView(menu, menuParams)
        menuView = menu

        mainHandler.postDelayed({ dismiss() }, 5000L)
    }

    fun dismiss() {
        menuView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        menuView = null
    }
}
