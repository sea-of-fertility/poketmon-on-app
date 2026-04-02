package com.example.poketmon_on_app.pet

data class PokemonData(
    val id: Int,
    val name: String,
    val gen: Int,
    val types: List<String>
) {
    val displayId: String get() = "#${id.toString().padStart(3, '0')}"
    val spriteFolder: String get() = String.format("Sprites/%04d", id)
}
