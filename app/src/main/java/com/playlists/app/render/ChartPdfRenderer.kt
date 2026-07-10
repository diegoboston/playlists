package com.playlists.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.playlists.app.ai.ChartDraft
import com.playlists.app.ai.normalizeOptionalField
import java.io.ByteArrayOutputStream
import kotlin.math.min

object ChartPdfRenderer {
    fun resolvedBodyTextSize(draft: ChartDraft, preferred: Float? = draft.bodyTextSize): Float {
        val items = ChartPdfLayout.flatten(
            draft.sections.map { ChartPdfLayout.Block(it.label, it.lines) },
        )
        return ChartPdfLayout.chooseTextSize(
            items = items,
            hasNotes = draft.notes.orEmpty().isNotEmpty(),
            preferredSize = preferred,
            lineHeight = { size, isTitle -> paintLineHeight(size, isTitle) },
        )
    }

    fun render(draft: ChartDraft, bodyTextSize: Float? = draft.bodyTextSize): ByteArray {
        val blocks = draft.sections.map { ChartPdfLayout.Block(it.label, it.lines) }
        val items = ChartPdfLayout.flatten(blocks)
        val hasNotes = draft.notes.orEmpty().isNotEmpty()

        val textSize = resolvedBodyTextSize(draft, bodyTextSize)

        val titlePaint = createPaint(Typeface.SANS_SERIF, Typeface.BOLD)
        val bodyPaint = createPaint(Typeface.MONOSPACE, Typeface.NORMAL)
        val chordPaint = createPaint(Typeface.MONOSPACE, Typeface.BOLD)
        val labelPaint = createPaint(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)

        titlePaint.textSize = min(ChartPdfLayout.MAX_TITLE_SIZE, textSize + 8f)
        bodyPaint.textSize = textSize
        chordPaint.textSize = textSize
        labelPaint.textSize = textSize

        val metrics = ChartPdfLayout.metricsFor(
            textSize = textSize,
            hasNotes = hasNotes,
            titleLineHeight = titlePaint.fontSpacing,
            labelLineHeight = labelPaint.fontSpacing,
            bodyLineHeight = bodyPaint.fontSpacing,
        )

        val pages = ChartPdfLayout.layoutPages(items, metrics)

        val document = PdfDocument()
        pages.forEachIndexed { index, pageItems ->
            val pageInfo = PdfDocument.PageInfo.Builder(
                PdfPageSpec.WIDTH,
                PdfPageSpec.HEIGHT,
                index + 1,
            ).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            var y = PdfPageSpec.MARGIN + titlePaint.fontSpacing
            if (index == 0) {
                y = drawHeader(canvas, draft, titlePaint, y)
                y += bodyPaint.fontSpacing * 0.5f
            } else {
                y = drawContinuationHeader(canvas, draft, titlePaint, y, index + 1, pages.size)
            }

            pageItems.forEach { item ->
                y = when (item) {
                    is ChartPdfLayout.Item.SectionLabel -> {
                        canvas.drawText(item.text, PdfPageSpec.MARGIN, y, labelPaint)
                        y + labelPaint.fontSpacing
                    }
                    is ChartPdfLayout.Item.Line -> {
                        drawLine(canvas, item.text, PdfPageSpec.MARGIN, y, bodyPaint, chordPaint)
                        y + bodyPaint.fontSpacing
                    }
                    ChartPdfLayout.Item.SectionGap -> y + bodyPaint.fontSpacing * 0.35f
                }
            }

            document.finishPage(page)
        }

        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    private fun createPaint(family: Typeface, style: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(family, style)
            textAlign = Paint.Align.LEFT
        }

    private fun paintLineHeight(textSize: Float, isTitle: Boolean): Float {
        val paint = createPaint(
            if (isTitle) Typeface.SANS_SERIF else Typeface.MONOSPACE,
            if (isTitle) Typeface.BOLD else Typeface.NORMAL,
        )
        paint.textSize = if (isTitle) min(ChartPdfLayout.MAX_TITLE_SIZE, textSize + 8f) else textSize
        return paint.fontSpacing
    }

    private fun drawHeader(canvas: Canvas, draft: ChartDraft, paint: Paint, y: Float): Float {
        val keyPart = draft.key.normalizeOptionalField()?.let { " ($it)" }.orEmpty()
        val artistPart = draft.artist.normalizeOptionalField()?.let { " — $it" }.orEmpty()
        canvas.drawText("${draft.title}$keyPart$artistPart", PdfPageSpec.MARGIN, y, paint)
        var nextY = y + paint.fontSpacing
        val meta = draft.notes.orEmpty()
        if (meta.isNotEmpty()) {
            paint.textSize = min(paint.textSize, 12f)
            canvas.drawText(meta, PdfPageSpec.MARGIN, nextY, paint)
            nextY += paint.fontSpacing
        }
        return nextY
    }

    private fun drawContinuationHeader(
        canvas: Canvas,
        draft: ChartDraft,
        paint: Paint,
        y: Float,
        pageNumber: Int,
        pageCount: Int,
    ): Float {
        val savedSize = paint.textSize
        paint.textSize = min(savedSize, 12f)
        val suffix = if (pageCount > 1) " ($pageNumber/$pageCount)" else ""
        canvas.drawText("${draft.title}$suffix", PdfPageSpec.MARGIN, y, paint)
        paint.textSize = savedSize
        return y + paint.fontSpacing * 0.85f
    }

    private fun drawLine(
        canvas: Canvas,
        line: String,
        x: Float,
        y: Float,
        bodyPaint: Paint,
        chordPaint: Paint,
    ) {
        var xPos = x
        for ((text, isChord) in parseLineSegments(line)) {
            val paint = if (isChord) chordPaint else bodyPaint
            canvas.drawText(text, xPos, y, paint)
            xPos += paint.measureText(text)
        }
    }

    private fun parseLineSegments(line: String): List<Pair<String, Boolean>> {
        if (!line.contains('<')) return listOf(line to false)
        val segments = mutableListOf<Pair<String, Boolean>>()
        val pattern = Regex("""<([^>]+)>|([^<]+)""")
        pattern.findAll(line).forEach { match ->
            when {
                match.groupValues[1].isNotEmpty() -> segments.add(match.groupValues[1] to true)
                match.groupValues[2].isNotEmpty() -> segments.add(match.groupValues[2] to false)
            }
        }
        return segments.ifEmpty { listOf(line to false) }
    }
}
