package com.example.poketmon_on_app.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class AnimInfo(
    val name: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val durations: List<Int> // in 1/60s units
)

class SpriteSheet(context: Context, pokemonId: Int) {

    private val folder = String.format("Sprites/%04d", pokemonId)
    val anims: Map<String, AnimInfo>
    private val sheets = mutableMapOf<String, Bitmap>()
    private val availableAnims: Set<String>

    init {
        anims = parseAnimData(context)
        availableAnims = checkAvailableAnims(context)
        for (name in listOf("Walk", "Idle", "Sleep")) {
            loadSheet(context, name)
        }
    }

    private fun checkAvailableAnims(context: Context): Set<String> {
        val result = mutableSetOf<String>()
        val files = try {
            context.assets.list(folder) ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        for (file in files) {
            if (file.endsWith("-Anim.png")) {
                result.add(file.removeSuffix("-Anim.png"))
            }
        }
        return result
    }

    fun hasAnimation(name: String): Boolean = name in availableAnims

    /**
     * Resolve animation with fallback chain:
     * - Idle not available → Walk
     * - Walk not available → Idle
     * - Sleep not available → resolved Idle
     */
    fun resolveAnimation(name: String): String? {
        if (name in availableAnims) return name
        return when (name) {
            "Idle" -> if ("Walk" in availableAnims) "Walk" else null
            "Walk" -> if ("Idle" in availableAnims) "Idle" else null
            "Sleep" -> resolveAnimation("Idle")
            else -> null
        }
    }

    /**
     * Available reaction animations (Hop, Hurt, Eat) that actually have sprite sheets.
     */
    fun availableReactions(): List<String> {
        return listOf("Hop", "Hurt", "Eat").filter { it in availableAnims }
    }

    private fun parseAnimData(context: Context): Map<String, AnimInfo> {
        val result = mutableMapOf<String, AnimInfo>()
        val input = context.assets.open("$folder/AnimData.xml")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, "UTF-8")

        var currentName: String? = null
        var frameWidth = 0
        var frameHeight = 0
        var durations = mutableListOf<Int>()
        var inDuration = false
        var inName = false
        var inFrameWidth = false
        var inFrameHeight = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "Anim" -> {
                        currentName = null
                        frameWidth = 0
                        frameHeight = 0
                        durations = mutableListOf()
                    }
                    "Name" -> inName = true
                    "FrameWidth" -> inFrameWidth = true
                    "FrameHeight" -> inFrameHeight = true
                    "Duration" -> inDuration = true
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text.trim()
                    if (text.isNotEmpty()) {
                        when {
                            inName -> currentName = text
                            inFrameWidth -> frameWidth = text.toInt()
                            inFrameHeight -> frameHeight = text.toInt()
                            inDuration -> durations.add(text.toInt())
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "Name" -> inName = false
                    "FrameWidth" -> inFrameWidth = false
                    "FrameHeight" -> inFrameHeight = false
                    "Duration" -> inDuration = false
                    "Anim" -> {
                        if (currentName != null && frameWidth > 0 && frameHeight > 0) {
                            result[currentName!!] = AnimInfo(currentName!!, frameWidth, frameHeight, durations.toList())
                        }
                    }
                }
            }
            parser.next()
        }
        input.close()
        return result
    }

    private fun loadSheet(context: Context, animName: String): Bitmap? {
        if (sheets.containsKey(animName)) return sheets[animName]
        return try {
            val path = "$folder/$animName-Anim.png"
            val input = context.assets.open(path)
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val bmp = BitmapFactory.decodeStream(input, null, opts)
            input.close()
            bmp?.also { sheets[animName] = it }
        } catch (_: Exception) {
            null
        }
    }

    private val frameCache = mutableMapOf<Long, Bitmap>()

    private fun frameCacheKey(animIndex: Int, frameIndex: Int, directionRow: Int): Long {
        return (animIndex.toLong() shl 32) or (directionRow.toLong() shl 16) or frameIndex.toLong()
    }

    fun getFrame(context: Context, animName: String, frameIndex: Int, directionRow: Int): Bitmap? {
        val info = anims[animName] ?: return null
        val sheet = loadSheet(context, animName) ?: return null

        val totalCols = sheet.width / info.frameWidth
        val totalRows = sheet.height / info.frameHeight
        val col = frameIndex % totalCols
        val row = if (directionRow < totalRows) directionRow else 0

        val key = frameCacheKey(anims.keys.indexOf(animName), col, row)
        frameCache[key]?.let { return it }

        val x = col * info.frameWidth
        val y = row * info.frameHeight
        if (x + info.frameWidth > sheet.width || y + info.frameHeight > sheet.height) return null

        val frame = Bitmap.createBitmap(sheet, x, y, info.frameWidth, info.frameHeight)
        frameCache[key] = frame
        return frame
    }

    fun getFrameCount(animName: String): Int {
        val info = anims[animName] ?: return 0
        return info.durations.size
    }
}
