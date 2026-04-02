package com.example.poketmon_on_app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import com.example.poketmon_on_app.pet.SpriteSheet

class PetView(context: Context) : View(context) {

    private var spriteSheet: SpriteSheet? = null
    private var currentAnim = "Idle"
    private var currentFrame = 0
    private var directionRow = 0 // 0=Down
    var speedMultiplier = 1.0f

    private val paint = Paint().apply {
        isFilterBitmap = false // nearest-neighbor for pixel art
        isAntiAlias = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            advanceFrame()
            invalidate()
            val delayMs = getFrameDelayMs()
            handler.postDelayed(this, delayMs)
        }
    }

    fun setSpriteSheet(sheet: SpriteSheet) {
        spriteSheet = sheet
        currentFrame = 0
    }

    fun setAnimation(animName: String) {
        val sheet = spriteSheet ?: return
        if (sheet.anims.containsKey(animName)) {
            currentAnim = animName
            currentFrame = 0
        }
    }

    fun setDirection(row: Int) {
        directionRow = row.coerceIn(0, 7)
    }

    fun startAnimation() {
        if (running) return
        running = true
        handler.post(frameRunnable)
    }

    fun stopAnimation() {
        running = false
        handler.removeCallbacks(frameRunnable)
    }

    private fun advanceFrame() {
        val sheet = spriteSheet ?: return
        val count = sheet.getFrameCount(currentAnim)
        if (count > 0) {
            currentFrame = (currentFrame + 1) % count
        }
    }

    private fun getFrameDelayMs(): Long {
        val sheet = spriteSheet ?: return 133L
        val info = sheet.anims[currentAnim] ?: return 133L
        val durationTicks = info.durations.getOrElse(currentFrame % info.durations.size) { 8 }
        return ((durationTicks * 1000L / 60L) / speedMultiplier.coerceAtLeast(0.1f)).toLong().coerceAtLeast(16L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sheet = spriteSheet ?: return
        val frame = sheet.getFrame(context, currentAnim, currentFrame, directionRow) ?: return
        drawScaledFrame(canvas, frame)
    }

    private fun drawScaledFrame(canvas: Canvas, frame: Bitmap) {
        val scale = (width.toFloat() / frame.width).coerceAtMost(height.toFloat() / frame.height)
        val scaledW = (frame.width * scale).toInt()
        val scaledH = (frame.height * scale).toInt()
        val left = (width - scaledW) / 2
        val top = (height - scaledH) / 2
        val destRect = Rect(left, top, left + scaledW, top + scaledH)
        canvas.drawBitmap(frame, null, destRect, paint)
    }
}
