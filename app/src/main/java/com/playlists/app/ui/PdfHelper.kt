package com.playlists.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

object PdfHelper {
    fun pageCount(file: File): Int =
        openRenderer(file)?.use { it.pageCount } ?: 0

    fun renderPage(file: File, pageIndex: Int, width: Int): Bitmap? {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        return try {
            if (pageIndex !in 0 until renderer.pageCount) return null
            renderer.openPage(pageIndex).use { page ->
                val scale = width.toFloat() / page.width
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    private fun openRenderer(file: File): PdfRenderer? {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            PdfRenderer(pfd)
        } catch (_: Exception) {
            pfd.close()
            null
        }
    }
}
