package com.playlists.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import kotlin.math.abs

object PlaceholderImageGenerator {
    private const val WIDTH = 1400
    private const val HEIGHT = 1800
    private const val HORIZONTAL_PADDING = 80

    fun render(title: String): ByteArray {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 72f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val maxWidth = WIDTH - HORIZONTAL_PADDING * 2
        val lines = wrapText(title.trim().ifBlank { "Untitled" }, paint, maxWidth)
        val lineHeight = paint.fontSpacing
        val totalHeight = lines.size * lineHeight
        var y = (HEIGHT - totalHeight) / 2f + abs(paint.descent())

        lines.forEach { line ->
            canvas.drawText(line, WIDTH / 2f, y, paint)
            y += lineHeight
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf("Untitled")

        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
