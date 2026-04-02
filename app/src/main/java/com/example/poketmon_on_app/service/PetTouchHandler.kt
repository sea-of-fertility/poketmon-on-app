package com.example.poketmon_on_app.service

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.poketmon_on_app.pet.PetState

class PetTouchHandler(
    private val longPressMs: Long = 500L,
    private val tapThreshold: Float = 10f
) : View.OnTouchListener {

    interface Callback {
        fun getCurrentState(): PetState?
        fun getLayoutParams(): WindowManager.LayoutParams?
        fun onTap()
        fun onLongPress()
        fun onDragStart()
        fun onDragEnd()
        fun dismissMenu()
        fun updateViewLayout(view: View)
    }

    var callback: Callback? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var longPressTriggered = false
    private var ignoringTouch = false

    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        longPressTriggered = true
        callback?.onLongPress()
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val cb = callback ?: return false
        val params = cb.getLayoutParams() ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (cb.getCurrentState() == PetState.REACTION) {
                    ignoringTouch = true
                    return true
                }
                ignoringTouch = false
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                longPressTriggered = false
                longPressHandler.postDelayed(longPressRunnable, longPressMs)
                cb.dismissMenu()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (ignoringTouch) return true
                val movedX = Math.abs(event.rawX - initialTouchX)
                val movedY = Math.abs(event.rawY - initialTouchY)
                if (movedX > tapThreshold || movedY > tapThreshold) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (!longPressTriggered) {
                        cb.onDragStart()
                    }
                }
                params.x = initialX + (event.rawX - initialTouchX).toInt()
                params.y = initialY + (event.rawY - initialTouchY).toInt()
                cb.updateViewLayout(v)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (ignoringTouch) {
                    ignoringTouch = false
                    return true
                }
                longPressHandler.removeCallbacks(longPressRunnable)
                val movedX = Math.abs(event.rawX - initialTouchX)
                val movedY = Math.abs(event.rawY - initialTouchY)
                if (!longPressTriggered && movedX < tapThreshold && movedY < tapThreshold) {
                    cb.onTap()
                } else if (!longPressTriggered) {
                    cb.onDragEnd()
                }
                v.performClick()
                return true
            }
        }
        return false
    }

    fun destroy() {
        longPressHandler.removeCallbacks(longPressRunnable)
        callback = null
    }
}
