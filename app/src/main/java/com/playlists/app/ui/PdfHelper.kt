package com.playlists.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.playlists.app.data.FileType
import com.playlists.app.util.FileStamp
import com.playlists.app.util.SongAnnotate
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object PdfHelper {
    private data class CachedRenderer(
        val path: String,
        val stamp: FileStamp,
        val pfd: ParcelFileDescriptor,
        val renderer: PdfRenderer,
    )

    private val pageCountCache = ConcurrentHashMap<String, Pair<FileStamp, Int>>()
    private val pageHasAnnotsCache = ConcurrentHashMap<String, Pair<FileStamp, BooleanArray>>()
    private val rendererLock = Any()
    private var cachedRenderer: CachedRenderer? = null

    fun pageCount(file: File, fileType: FileType): Int = when (fileType) {
        FileType.IMAGE -> 1
        FileType.PDF -> pageCount(file)
    }

    fun pageCount(file: File): Int {
        val stamp = SongAnnotate.stampOf(file)
        val cached = pageCountCache[file.absolutePath]
        if (cached != null && cached.first == stamp) return cached.second
        val count = synchronized(rendererLock) { rendererForLocked(file)?.pageCount ?: 0 }
        pageCountCache[file.absolutePath] = stamp to count
        return count
    }

    fun invalidate(file: File) {
        val path = file.absolutePath
        pageCountCache.remove(path)
        pageHasAnnotsCache.remove(path)
        synchronized(rendererLock) {
            val held = cachedRenderer
            if (held != null && held.path == path) {
                closeCachedRendererLocked()
            }
        }
    }

    fun renderPage(file: File, pageIndex: Int, width: Int): Bitmap? {
        if (pageHasAnnotations(file, pageIndex)) {
            renderPageWithPdfBox(file, pageIndex, width)?.let { return it }
        }
        return renderPagePlatform(file, pageIndex, width)
    }

    private fun pageHasAnnotations(file: File, pageIndex: Int): Boolean {
        val stamp = SongAnnotate.stampOf(file)
        val cached = pageHasAnnotsCache[file.absolutePath]
        val flags = if (cached != null && cached.first == stamp) {
            cached.second
        } else {
            val loaded = try {
                PDDocument.load(file).use { doc ->
                    BooleanArray(doc.numberOfPages) { i ->
                        doc.getPage(i).annotations.orEmpty().isNotEmpty()
                    }
                }
            } catch (_: Exception) {
                BooleanArray(0)
            }
            pageHasAnnotsCache[file.absolutePath] = stamp to loaded
            loaded
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
        return synchronized(rendererLock) {
            val renderer = rendererForLocked(file) ?: return@synchronized null
            if (pageIndex !in 0 until renderer.pageCount) return@synchronized null
            renderer.openPage(pageIndex).use { page ->
                val scale = width.toFloat() / page.width
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
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

    private fun rendererForLocked(file: File): PdfRenderer? {
        val stamp = SongAnnotate.stampOf(file)
        val held = cachedRenderer
        if (held != null && held.path == file.absolutePath && held.stamp == stamp) {
            return held.renderer
        }
        closeCachedRendererLocked()
        val opened = openRenderer(file) ?: return null
        cachedRenderer = CachedRenderer(file.absolutePath, stamp, opened.first, opened.second)
        return opened.second
    }

    private fun closeCachedRendererLocked() {
        val held = cachedRenderer ?: return
        cachedRenderer = null
        runCatching { held.renderer.close() }
        runCatching { held.pfd.close() }
    }

    private fun openRenderer(file: File): Pair<ParcelFileDescriptor, PdfRenderer>? {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            pfd to PdfRenderer(pfd)
        } catch (_: Exception) {
            pfd.close()
            null
        }
    }
}
