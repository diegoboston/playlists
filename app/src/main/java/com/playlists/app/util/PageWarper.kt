package com.playlists.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import java.io.File
import kotlin.math.max

/** Perspective-warp a photo onto a rectangle. Resamples existing pixels only. */
object PageWarper {
    fun orientedSize(file: File): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return when (exifOrientation(file)) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE,
            -> bounds.outHeight to bounds.outWidth
            else -> bounds.outWidth to bounds.outHeight
        }
    }

    fun suggestNormalizedQuad(file: File): PageQuad {
        val bitmap = decodeOriented(file, maxEdge = 1000) ?: return PageQuad.normalizedInset()
        return try {
            val luma = lumaOf(bitmap)
            PageDetector.detectNormalized(luma, bitmap.width, bitmap.height)
                ?: PageQuad.normalizedInset()
        } finally {
            bitmap.recycle()
        }
    }

    fun warpToFile(file: File, normalizedQuad: PageQuad): Boolean {
        if (!PageGeometry.isUsableNormalized(normalizedQuad)) return false
        val bitmap = decodeOriented(file, maxEdge = PageGeometry.MAX_EDGE) ?: return false
        return try {
            val pixelQuad = normalizedQuad.toPixels(bitmap.width, bitmap.height)
            val (destWidth, destHeight) = PageGeometry.destSize(pixelQuad)
            val matrix = Matrix()
            val src = floatArrayOf(
                pixelQuad.topLeft.x, pixelQuad.topLeft.y,
                pixelQuad.topRight.x, pixelQuad.topRight.y,
                pixelQuad.bottomRight.x, pixelQuad.bottomRight.y,
                pixelQuad.bottomLeft.x, pixelQuad.bottomLeft.y,
            )
            val dst = floatArrayOf(
                0f, 0f,
                destWidth.toFloat(), 0f,
                destWidth.toFloat(), destHeight.toFloat(),
                0f, destHeight.toFloat(),
            )
            if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return false
            val out = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(out)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                file.outputStream().use { stream ->
                    out.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                }
            } finally {
                out.recycle()
            }
            true
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeOriented(file: File, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longest = max(bounds.outWidth, bounds.outHeight)
        var inSampleSize = 1
        while (longest / inSampleSize > maxEdge * 2) {
            inSampleSize *= 2
        }
        val raw = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { this.inSampleSize = inSampleSize },
        ) ?: return null
        val oriented = applyExif(raw, exifOrientation(file))
        val longestOriented = max(oriented.width, oriented.height)
        if (longestOriented <= maxEdge) return oriented
        val scale = maxEdge.toFloat() / longestOriented
        val scaled = Bitmap.createScaledBitmap(
            oriented,
            (oriented.width * scale).toInt().coerceAtLeast(1),
            (oriented.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== oriented) oriented.recycle()
        return scaled
    }

    private fun applyExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.preRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.preRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.preRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.preRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.preRotate(270f)
            else -> return bitmap
        }
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (out != bitmap) bitmap.recycle()
        return out
    }

    private fun exifOrientation(file: File): Int = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun lumaOf(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val luma = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
        }
        return luma
    }
}
