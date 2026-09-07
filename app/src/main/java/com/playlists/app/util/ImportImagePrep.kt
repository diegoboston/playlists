package com.playlists.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Downscale a stored image so OpenAI vision OCR stays small and cheap. */
object ImportImagePrep {
    fun jpegBytesForOcr(file: File, maxEdge: Int = 1280, quality: Int = 70): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longest = max(bounds.outWidth, bounds.outHeight)
        var inSampleSize = 1
        while (longest / inSampleSize > maxEdge * 2) {
            inSampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { this.inSampleSize = inSampleSize },
        ) ?: return null
        return try {
            val longestDecoded = max(bitmap.width, bitmap.height)
            val toEncode = if (longestDecoded > maxEdge) {
                val scale = maxEdge.toFloat() / longestDecoded
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }
            try {
                val out = ByteArrayOutputStream()
                if (!toEncode.compress(Bitmap.CompressFormat.JPEG, quality, out)) return null
                out.toByteArray()
            } finally {
                if (toEncode !== bitmap) toEncode.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun writeJpegFile(file: File, imageBytes: ByteArray, quality: Int = 85): Boolean {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return false
        return try {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
