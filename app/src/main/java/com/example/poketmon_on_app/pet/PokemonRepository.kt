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
        type: String? = null,
        query: String = ""
    ): List<PokemonData> {
        var list = allPokemon
        if (gen != null) {
            list = list.filter { it.gen == gen }
        }
        if (type != null) {
            list = list.filter { type in it.types }
        }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            list = list.filter { it.name.lowercase().contains(q) }
        }
        return list
    }

    fun getThumbnail(pokemonId: Int): Bitmap? {
        thumbnailCache.get(pokemonId)?.let { return it }

        val folder = String.format("Sprites/%04d", pokemonId)
        val animName = "Idle"
        val path = "$folder/$animName-Anim.png"

        return try {
            val input = context.assets.open(path)
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val sheet = BitmapFactory.decodeStream(input, null, opts)
            input.close()

            if (sheet != null) {
                val info = parseFrameSize(folder)
                val frameW = info.first
                val frameH = info.second
                if (frameW > 0 && frameH > 0 && sheet.width >= frameW && sheet.height >= frameH) {
                    val frame = Bitmap.createBitmap(sheet, 0, 0, frameW, frameH)
                    sheet.recycle()
                    thumbnailCache.put(pokemonId, frame)
                    frame
                } else {
                    sheet.recycle()
                    null
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFrameSize(folder: String): Pair<Int, Int> {
        return try {
            val input = context.assets.open("$folder/AnimData.xml")
            val text = input.bufferedReader().readText()
            input.close()

            val widthRegex = Regex("<FrameWidth>(\\d+)</FrameWidth>")
            val heightRegex = Regex("<FrameHeight>(\\d+)</FrameHeight>")
            val w = widthRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val h = heightRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            Pair(w, h)
        } catch (_: Exception) {
            Pair(0, 0)
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
