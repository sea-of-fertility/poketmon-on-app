package com.example.poketmon_on_app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.view.Choreographer
import android.view.View
import com.example.poketmon_on_app.pet.SpriteSheet

class PetView(context: Context) : View(context) {

    private var spriteSheet: SpriteSheet? = null
    private var currentAnim = "Idle"
    private var currentFrame = 0
    private var directionRow = 0 // 0=Down
    var speedMultiplier = 1.0f
    /** 최대 프레임 크기 — 모든 애니메이션에 동일 스케일 적용용 */
    var maxFrameWidth: Int = 0
    var maxFrameHeight: Int = 0
    private var playOnce = false
    private var animationDone = false
    private var onAnimationFinished: (() -> Unit)? = null

    private val paint = Paint().apply {
        isFilterBitmap = false // nearest-neighbor for pixel art
        isAntiAlias = false
    }

    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var frameGeneration = 0
    private var frameStartNanos = 0L
    private var currentFrameDurationMs = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return

            if (frameStartNanos == 0L) {
                frameStartNanos = frameTimeNanos
                currentFrameDurationMs = getFrameDelayMs()
                invalidate()
                choreographer.postFrameCallback(this)
                return
            }

            val elapsedMs = (frameTimeNanos - frameStartNanos) / 1_000_000
            if (elapsedMs >= currentFrameDurationMs) {
                val gen = frameGeneration
                advanceFrame()
                if (gen != frameGeneration) return
                frameStartNanos = frameTimeNanos
                currentFrameDurationMs = getFrameDelayMs()
                invalidate()
            }

            choreographer.postFrameCallback(this)
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
            playOnce = false
            animationDone = false
            onAnimationFinished = null
            invalidate()
            restartFrameTimer()
        }
    }

    fun playAnimationOnce(animName: String, onFinished: () -> Unit) {
        val sheet = spriteSheet ?: return
        if (sheet.anims.containsKey(animName)) {
            currentAnim = animName
            currentFrame = 0
            playOnce = true
            animationDone = false
            onAnimationFinished = onFinished
            invalidate()
            restartFrameTimer()
        }
    }

    fun setDirection(row: Int) {
        directionRow = row.coerceIn(0, 7)
    }

    fun startAnimation() {
        if (running) return
        running = true
        frameStartNanos = 0L
        choreographer.postFrameCallback(frameCallback)
    }

    fun stopAnimation() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    private fun restartFrameTimer() {
        choreographer.removeFrameCallback(frameCallback)
        frameGeneration++
        frameStartNanos = 0L
        if (running) {
            choreographer.postFrameCallback(frameCallback)
        }
    }

    private fun advanceFrame() {
        if (animationDone) return
        val sheet = spriteSheet ?: return
        val count = sheet.getFrameCount(currentAnim)
        if (count <= 0) return

        val nextFrame = currentFrame + 1
        if (playOnce && nextFrame >= count) {
            currentFrame = count - 1
            playOnce = false
            animationDone = true
            val cb = onAnimationFinished
            onAnimationFinished = null
            cb?.invoke()
            return
        }
        currentFrame = nextFrame % count
    }

    private fun getFrameDelayMs(): Long {
        val sheet = spriteSheet ?: return 133L
        val info = sheet.anims[currentAnim] ?: return 133L
        val durationTicks = info.durations.getOrElse(currentFrame % info.durations.size) { 8 }
        return ((durationTicks * 1000L / 60L) / speedMultiplier.coerceAtLeast(0.1f)).toLong().coerceAtLeast(16L)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val sheet = spriteSheet ?: return
        val frame = sheet.getFrame(context, currentAnim, currentFrame, directionRow) ?: return
        drawScaledFrame(canvas, frame)
    }

    private fun drawScaledFrame(canvas: Canvas, frame: Bitmap) {
        if (width <= 0 || height <= 0) return

        // maxFrame 기준 고정 스케일 — 애니메이션 전환 시 크기 점프 방지
        val refW = if (maxFrameWidth > 0) maxFrameWidth else frame.width
        val refH = if (maxFrameHeight > 0) maxFrameHeight else frame.height
        val scale = minOf(
            width.toFloat() / refW.toFloat(),
            height.toFloat() / refH.toFloat()
        )
        val dstW = (frame.width * scale).toInt()
        val dstH = (frame.height * scale).toInt()
        val left = (width - dstW) / 2
        val top = height - dstH

        canvas.drawBitmap(frame, null, Rect(left, top, left + dstW, top + dstH), paint)
    }
}
