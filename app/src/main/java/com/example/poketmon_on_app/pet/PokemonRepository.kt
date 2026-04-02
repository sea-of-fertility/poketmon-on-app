package com.example.poketmon_on_app.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import org.json.JSONArray

class PokemonRepository(private val context: Context) {

    private val allPokemon: List<PokemonData> by lazy { loadFromJson() }
    private val availableIds: Set<Int> by lazy { checkAvailableSprites() }

    private val thumbnailCache = LruCache<Int, Bitmap>(100)

    fun getAll(): List<PokemonData> = allPokemon

    fun isAvailable(id: Int): Boolean = id in availableIds

    fun getAllTypes(): List<String> {
        return allPokemon.flatMap { it.types }.distinct().sorted()
    }

    fun getGenerations(): List<Int> {
        return allPokemon.map { it.gen }.distinct().sorted()
    }

    fun filter(
        gen: Int? = null,
        gens: Set<Int> = emptySet(),
        type: String? = null,
        types: Set<String> = emptySet(),
        query: String = ""
    ): List<PokemonData> {
        var list = allPokemon
        if (gens.isNotEmpty()) {
            list = list.filter { it.gen in gens }
        } else if (gen != null) {
            list = list.filter { it.gen == gen }
        }
        if (types.isNotEmpty()) {
            list = list.filter { pokemon -> pokemon.types.any { it in types } }
        } else if (type != null) {
            list = list.filter { type in it.types }
        }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            list = list.filter { it.name.lowercase().contains(q) }
        }
        return list
    }

    fun getPortrait(pokemonId: Int): Bitmap? {
        thumbnailCache.get(pokemonId)?.let { return it }

        val path = String.format("Resources/Portraits/%04d.png", pokemonId)
        return try {
            val input = context.assets.open(path)
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val bmp = BitmapFactory.decodeStream(input, null, opts)
            input.close()
            bmp?.also { thumbnailCache.put(pokemonId, it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadFromJson(): List<PokemonData> {
        return try {
            val input = context.assets.open("pokemon_data.json")
            val text = input.bufferedReader().readText()
            input.close()

            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val types = (0 until obj.getJSONArray("types").length()).map { j ->
                    obj.getJSONArray("types").getString(j)
                }
                PokemonData(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    gen = obj.getInt("gen"),
                    types = types
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun checkAvailableSprites(): Set<Int> {
        return try {
            val folders = context.assets.list("Sprites") ?: emptyArray()
            folders.mapNotNull { it.toIntOrNull() }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
