package com.playlists.app.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import com.playlists.app.util.FileStorage
import java.io.File
import kotlin.math.min

/** Combine camera/gallery JPEGs into one PDF (one image per page). */
object ImagePagesPdf {
    fun shouldCombine(pageCount: Int): Boolean = pageCount > 1

    fun write(pages: List<File>): File {
        val existing = pages.filter { it.exists() && it.length() > 0L }
        require(existing.isNotEmpty()) { "No page images to combine" }
        val document = PdfDocument()
        var added = 0
        try {
            existing.forEach { file ->
                val bitmap = decodeForPage(file) ?: return@forEach
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        PdfPageSpec.WIDTH,
                        PdfPageSpec.HEIGHT,
                        added + 1,
                    ).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    val dest = fit(bitmap.width, bitmap.height, PdfPageSpec.WIDTH, PdfPageSpec.HEIGHT)
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        android.graphics.Rect(dest.left, dest.top, dest.right, dest.bottom),
                        null,
                    )
                    document.finishPage(page)
                    added++
                } finally {
                    bitmap.recycle()
                }
            }
            require(added > 0) { "Could not decode any page images" }
            val out = File(FileStorage.songsDir(), "${java.util.UUID.randomUUID()}.pdf")
            out.outputStream().use { document.writeTo(it) }
            return out
        } finally {
            document.close()
        }
    }

    data class Fit(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    internal fun fit(imageWidth: Int, imageHeight: Int, pageWidth: Int, pageHeight: Int): Fit {
        val scale = min(
            pageWidth.toFloat() / imageWidth.coerceAtLeast(1),
            pageHeight.toFloat() / imageHeight.coerceAtLeast(1),
        )
        val width = (imageWidth * scale).toInt().coerceAtLeast(1)
        val height = (imageHeight * scale).toInt().coerceAtLeast(1)
        val left = (pageWidth - width) / 2
        val top = (pageHeight - height) / 2
        return Fit(left, top, left + width, top + height)
    }

    private fun decodeForPage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var inSampleSize = 1
        while (longest / inSampleSize > PdfPageSpec.MEDIA_RENDER_WIDTH * 2) {
            inSampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { this.inSampleSize = inSampleSize },
        )
    }
}
