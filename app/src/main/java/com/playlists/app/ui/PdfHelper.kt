package com.playlists.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.playlists.app.data.FileType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object PdfHelper {
    private val pageCountCache = ConcurrentHashMap<String, Int>()
    private val pageHasAnnotsCache = ConcurrentHashMap<String, BooleanArray>()

    fun pageCount(file: File, fileType: FileType): Int = when (fileType) {
        FileType.IMAGE -> 1
        FileType.PDF -> pageCount(file)
    }

    fun pageCount(file: File): Int =
        pageCountCache.getOrPut(file.absolutePath) {
            openRenderer(file)?.use { it.pageCount } ?: 0
        }

    fun invalidate(file: File) {
        val path = file.absolutePath
        pageCountCache.remove(path)
        pageHasAnnotsCache.remove(path)
    }

    fun renderPage(file: File, pageIndex: Int, width: Int): Bitmap? {
        if (pageHasAnnotations(file, pageIndex)) {
            renderPageWithPdfBox(file, pageIndex, width)?.let { return it }
        }
        return renderPagePlatform(file, pageIndex, width)
    }

    private fun pageHasAnnotations(file: File, pageIndex: Int): Boolean {
        val flags = pageHasAnnotsCache.getOrPut(file.absolutePath) {
            try {
                PDDocument.load(file).use { doc ->
                    BooleanArray(doc.numberOfPages) { i ->
                        doc.getPage(i).annotations.orEmpty().isNotEmpty()
                    }
                }
            } catch (_: Exception) {
                BooleanArray(0)
            }
        }
        return pageIndex in flags.indices && flags[pageIndex]
    }

    private fun renderPageWithPdfBox(file: File, pageIndex: Int, width: Int): Bitmap? {
        return try {
            PDDocument.load(file).use { doc ->
                if (pageIndex !in 0 until doc.numberOfPages) return null
                val pageWidth = doc.getPage(pageIndex).mediaBox.width
                if (pageWidth <= 0f) return null
                val scale = width.toFloat() / pageWidth
                val rendered = PDFRenderer(doc).renderImage(pageIndex, scale) ?: return null
                ensureWhiteBackground(rendered)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun renderPagePlatform(file: File, pageIndex: Int, width: Int): Bitmap? {
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

    private fun ensureWhiteBackground(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.RGB_565) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        Canvas(out).drawBitmap(bitmap, 0f, 0f, null)
        if (out != bitmap) {
            bitmap.recycle()
        }
        return out
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
