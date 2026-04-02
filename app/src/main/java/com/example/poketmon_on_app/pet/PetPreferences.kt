package com.example.poketmon_on_app.pet

import android.content.Context
import android.content.SharedPreferences

class PetPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pokepet_prefs", Context.MODE_PRIVATE)

    var selectedPokemonId: Int
        get() = prefs.getInt("selected_pokemon_id", 25)
        set(value) = prefs.edit().putInt("selected_pokemon_id", value).apply()

    var isServiceRunning: Boolean
        get() = prefs.getBoolean("service_running", false)
        set(value) = prefs.edit().putBoolean("service_running", value).apply()

    // Appearance
    var scale: Int
        get() = prefs.getInt("scale", 100)
        set(value) = prefs.edit().putInt("scale", value).apply()

    var opacity: Int
        get() = prefs.getInt("opacity", 100)
        set(value) = prefs.edit().putInt("opacity", value).apply()

    // Behavior
    var moveSpeedLevel: Int
        get() = prefs.getInt("move_speed", 3)
        set(value) = prefs.edit().putInt("move_speed", value).apply()

    var activityLevel: Int
        get() = prefs.getInt("activity_level", 3)
        set(value) = prefs.edit().putInt("activity_level", value).apply()

    var sleepTimeout: Int
        get() = prefs.getInt("sleep_timeout", 3)
        set(value) = prefs.edit().putInt("sleep_timeout", value).apply()

    // Game mode
    var hideInGame: Boolean
        get() = prefs.getBoolean("hide_in_game", false)
        set(value) = prefs.edit().putBoolean("hide_in_game", value).apply()

    // Speed level → px/frame
    fun getMoveSpeedPx(): Float {
        return when (moveSpeedLevel) {
            1 -> 1.0f
            2 -> 1.5f
            3 -> 2.0f
            4 -> 3.0f
            5 -> 4.0f
            else -> 2.0f
        }
    }
}
