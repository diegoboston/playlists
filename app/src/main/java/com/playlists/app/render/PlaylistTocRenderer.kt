package com.playlists.app.render

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.playlists.app.ui.SongDisplay
import com.playlists.app.ui.TocEntry
import java.io.File

object PlaylistTocRenderer {
    private const val TITLE_SIZE = 22f
    private const val BODY_SIZE = 16f

    fun renderToFile(output: File, playlistName: String, entries: List<TocEntry>): Int {
        val document = PdfDocument()
        val pageCount = render(document, playlistName, entries)
        output.outputStream().use { document.writeTo(it) }
        document.close()
        return pageCount
    }

    fun render(document: PdfDocument, playlistName: String, entries: List<TocEntry>): Int {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = TITLE_SIZE
            textAlign = Paint.Align.LEFT
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = BODY_SIZE
            textAlign = Paint.Align.LEFT
        }
        val contentWidth = PdfPageSpec.WIDTH - PdfPageSpec.MARGIN * 2
        val lines = entries
            .map { SongDisplay.adjustedSongTitle(it.title, it.keySignature) }
            .flatMap { wrapText(it, bodyPaint, contentWidth) }

        var pageNumber = 0
        var lineIndex = 0
        while (lineIndex < lines.size || pageNumber == 0) {
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(
                PdfPageSpec.WIDTH,
                PdfPageSpec.HEIGHT,
                document.pages.size + 1,
            ).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            var y = PdfPageSpec.MARGIN + titlePaint.fontSpacing
            if (pageNumber == 1) {
                canvas.drawText(playlistName.trim().ifBlank { "Playlist" }, PdfPageSpec.MARGIN, y, titlePaint)
                y += titlePaint.fontSpacing * 1.5f
            }

            while (lineIndex < lines.size) {
                val baseline = y + bodyPaint.textSize
                if (baseline + bodyPaint.descent() > PdfPageSpec.HEIGHT - PdfPageSpec.MARGIN && lineIndex > 0) {
                    break
                }
                canvas.drawText(lines[lineIndex], PdfPageSpec.MARGIN, baseline, bodyPaint)
                y += bodyPaint.fontSpacing
                lineIndex++
            }

            document.finishPage(page)
            if (lineIndex >= lines.size) break
        }
        return pageNumber
    }

    internal fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> =
        wrapText(text, maxWidth) { segment -> paint.measureText(segment) }

    internal fun wrapText(text: String, maxWidth: Float, measureWidth: (String) -> Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = start + 1
            while (end <= text.length && measureWidth(text.substring(start, end)) <= maxWidth) {
                end++
            }
            if (end - 1 > start) {
                end--
            }
            if (end < text.length && !text[end].isWhitespace()) {
                val segment = text.substring(start, end)
                val lastSpace = segment.lastIndexOf(' ')
                if (lastSpace > 0) {
                    end = start + lastSpace + 1
                }
            }
            lines.add(text.substring(start, end).trim())
            start = end
            while (start < text.length && text[start].isWhitespace()) start++
        }
        return lines.ifEmpty { listOf(text) }
    }
}
