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

    init {
        anims = parseAnimData(context)
        // Preload essential sheets
        for (name in listOf("Walk", "Idle", "Sleep")) {
            loadSheet(context, name)
        }
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
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract a single frame from the sprite sheet.
     * @param animName e.g. "Walk", "Idle"
     * @param frameIndex column index
     * @param directionRow row index (0=Down, 1=DownRight, ... 7=DownLeft)
     */
    fun getFrame(context: Context, animName: String, frameIndex: Int, directionRow: Int): Bitmap? {
        val info = anims[animName] ?: return null
        val sheet = loadSheet(context, animName) ?: return null

        val totalCols = sheet.width / info.frameWidth
        val totalRows = sheet.height / info.frameHeight
        val col = frameIndex % totalCols
        val row = if (directionRow < totalRows) directionRow else 0

        val x = col * info.frameWidth
        val y = row * info.frameHeight
        if (x + info.frameWidth > sheet.width || y + info.frameHeight > sheet.height) return null

        return Bitmap.createBitmap(sheet, x, y, info.frameWidth, info.frameHeight)
    }

    fun getFrameCount(animName: String): Int {
        val info = anims[animName] ?: return 0
        return info.durations.size
    }
}
