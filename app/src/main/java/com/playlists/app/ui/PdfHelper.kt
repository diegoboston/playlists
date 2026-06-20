package com.playlists.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.shockwave.pdfium.PdfiumCore
import java.io.File

object PdfHelper {
    fun pageCount(context: android.content.Context, file: File): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            openRenderer(file)?.use { it.pageCount } ?: pdfiumPageCount(context, file)
        } else {
            pdfiumPageCount(context, file)
        }
    }

    fun renderPage(
        context: android.content.Context,
        file: File,
        pageIndex: Int,
        width: Int,
    ): Bitmap? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            renderWithPdfRenderer(file, pageIndex, width)
        } else {
            renderWithPdfium(context, file, pageIndex, width)
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

    private fun renderWithPdfRenderer(file: File, pageIndex: Int, width: Int): Bitmap? {
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

    private fun pdfiumPageCount(context: android.content.Context, file: File): Int {
        val core = PdfiumCore(context)
        val doc = core.newDocument(readBytes(file), null)
        return try {
            core.getPageCount(doc)
        } finally {
            core.closeDocument(doc)
        }
    }

    private fun renderWithPdfium(
        context: android.content.Context,
        file: File,
        pageIndex: Int,
        width: Int,
    ): Bitmap? {
        val core = PdfiumCore(context)
        val doc = core.newDocument(readBytes(file), null)
        return try {
            if (pageIndex !in 0 until core.getPageCount(doc)) return null
            core.openPage(doc, pageIndex)
            val pageWidth = core.getPageWidthPoint(doc, pageIndex)
            val pageHeight = core.getPageHeightPoint(doc, pageIndex)
            val scale = width.toFloat() / pageWidth
            val height = (pageHeight * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            core.renderPageBitmap(doc, bitmap, pageIndex, 0, 0, width, height)
            bitmap
        } finally {
            core.closeDocument(doc)
        }
    }

    private fun readBytes(file: File): ByteArray = file.readBytes()
}
