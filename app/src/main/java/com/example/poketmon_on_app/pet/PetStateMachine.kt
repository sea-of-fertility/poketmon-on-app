package com.example.poketmon_on_app.pet

import kotlin.random.Random

enum class PetState(val displayLabel: String) {
    IDLE("대기 중"),
    WALK("걷는 중"),
    RUN("뛰는 중"),
    SLEEP("자는 중"),
    REACTION("반응 중"),
    DRAGGED("드래그 중")
}

private data class TransitionTiming(val minDelayMs: Long, val spreadSeconds: Float)

class PetStateMachine(
    private val spriteSheet: SpriteSheet,
    private val onStateChanged: (PetState, String) -> Unit
) {

    var currentState: PetState = PetState.IDLE
        private set

    // Settings (default = activity level 3)
    var baseSpeed: Float = 2.0f        // px per frame
    var activityLevel: Int = 3          // 1~5
    var sleepTimeoutMs: Long = 3 * 60 * 1000L // 3 minutes

    private var stateTimer = 0L
    private var lastUpdateTime = System.currentTimeMillis()
    private var idleSinceMs = System.currentTimeMillis()

    // Movement
    var dx: Float = 0f
        private set
    var dy: Float = 0f
        private set
    var direction: Direction = Direction.DOWN
        private set

    fun update() {
        val now = System.currentTimeMillis()
        val dt = now - lastUpdateTime
        lastUpdateTime = now
        stateTimer += dt

        when (currentState) {
            PetState.IDLE -> {
                val idleMs = now - idleSinceMs
                if (idleMs >= sleepTimeoutMs) {
                    transitionTo(PetState.SLEEP)
                    return
                }
                val timing = idleTransitionTiming()
                if (shouldTransition(stateTimer, dt, timing)) {
                    transitionTo(PetState.WALK)
                }
            }
            PetState.WALK -> {
                val timing = walkDurationTiming()
                if (shouldTransition(stateTimer, dt, timing)) {
                    transitionTo(PetState.IDLE)
                }
            }
            PetState.RUN -> {
                if (stateTimer >= 10_000L) {
                    transitionTo(PetState.WALK)
                }
            }
            PetState.REACTION -> {
                // Handled externally when animation finishes
            }
            PetState.SLEEP, PetState.DRAGGED -> {
                // No auto-transition
            }
        }
    }

    fun transitionTo(state: PetState) {
        currentState = state
        stateTimer = 0

        when (state) {
            PetState.IDLE -> {
                dx = 0f
                dy = 0f
                idleSinceMs = System.currentTimeMillis()
                val anim = spriteSheet.resolveAnimation("Idle") ?: "Idle"
                onStateChanged(state, anim)
            }
            PetState.WALK -> {
                pickRandomDirection()
                val anim = spriteSheet.resolveAnimation("Walk") ?: spriteSheet.resolveAnimation("Idle") ?: "Idle"
                onStateChanged(state, anim)
            }
            PetState.RUN -> {
                pickRandomDirection()
                val anim = spriteSheet.resolveAnimation("Walk") ?: spriteSheet.resolveAnimation("Idle") ?: "Idle"
                onStateChanged(state, anim)
            }
            PetState.SLEEP -> {
                dx = 0f
                dy = 0f
                val anim = spriteSheet.resolveAnimation("Sleep") ?: spriteSheet.resolveAnimation("Idle") ?: "Idle"
                onStateChanged(state, anim)
            }
            PetState.REACTION -> {
                dx = 0f
                dy = 0f
                // Animation is set externally
            }
            PetState.DRAGGED -> {
                dx = 0f
                dy = 0f
            }
        }
    }

    fun getSpeedMultiplier(): Float {
        return when (currentState) {
            PetState.RUN -> 1.5f
            else -> 1.0f
        }
    }

    fun getMoveSpeed(): Float {
        return when (currentState) {
            PetState.WALK -> baseSpeed * 1.1f
            PetState.RUN -> baseSpeed * 2.0f
            else -> 0f
        }
    }

    fun bounceX() {
        dx = -dx
        direction = Direction.from(dx, dy)
    }

    fun bounceY() {
        dy = -dy
        direction = Direction.from(dx, dy)
    }

    private fun pickRandomDirection() {
        val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
        val speed = getMoveSpeed()
        dx = kotlin.math.cos(angle) * speed
        dy = kotlin.math.sin(angle) * speed
        direction = Direction.from(dx, dy)
    }

    private fun shouldTransition(elapsed: Long, dt: Long, timing: TransitionTiming): Boolean {
        return elapsed >= timing.minDelayMs &&
            Random.nextFloat() < dt.toFloat() / (timing.spreadSeconds * 1000f)
    }

    private fun idleTransitionTiming(): TransitionTiming = when (activityLevel) {
        1 -> TransitionTiming(5000L, 10f)
        2 -> TransitionTiming(3000L, 7f)
        3 -> TransitionTiming(2000L, 5f)
        4 -> TransitionTiming(1000L, 3f)
        5 -> TransitionTiming(500L, 2f)
        else -> TransitionTiming(2000L, 5f)
    }

    private fun walkDurationTiming(): TransitionTiming = when (activityLevel) {
        1 -> TransitionTiming(2000L, 4f)
        2 -> TransitionTiming(2500L, 6f)
        3 -> TransitionTiming(3000L, 10f)
        4 -> TransitionTiming(5000L, 15f)
        5 -> TransitionTiming(8000L, 20f)
        else -> TransitionTiming(3000L, 10f)
    }
}
