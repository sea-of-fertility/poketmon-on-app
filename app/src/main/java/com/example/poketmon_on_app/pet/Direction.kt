package com.example.poketmon_on_app.pet

import kotlin.math.atan2

enum class Direction(val row: Int) {
    DOWN(0),
    DOWN_RIGHT(1),
    RIGHT(2),
    UP_RIGHT(3),
    UP(4),
    UP_LEFT(5),
    LEFT(6),
    DOWN_LEFT(7);

    companion object {
        fun from(dx: Float, dy: Float): Direction {
            if (dx == 0f && dy == 0f) return DOWN
            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).let {
                if (it < 0) it + 360 else it
            }
            return when {
                angle < 22.5 || angle >= 337.5 -> RIGHT
                angle < 67.5 -> DOWN_RIGHT
                angle < 112.5 -> DOWN
                angle < 157.5 -> DOWN_LEFT
                angle < 202.5 -> LEFT
                angle < 247.5 -> UP_LEFT
                angle < 292.5 -> UP
                else -> UP_RIGHT
            }
        }
    }
}
