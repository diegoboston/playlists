package com.playlists.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.playlists.app.ai.ChartDraft
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.min

object ChartPdfRenderer {
    private const val PAGE_WIDTH = 612
    private const val PAGE_HEIGHT = 792
    private const val MARGIN = 48f
    private const val MIN_TEXT_SIZE = 10f
    private const val MAX_TITLE_SIZE = 28f
    private const val MAX_BODY_SIZE = 16f

    fun render(draft: ChartDraft): ByteArray {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        val chordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
            textAlign = Paint.Align.LEFT
        }

        var textSize = MAX_BODY_SIZE
        val blocks = buildBlocks(draft)
        while (textSize >= MIN_TEXT_SIZE) {
            titlePaint.textSize = min(MAX_TITLE_SIZE, textSize + 8f)
            bodyPaint.textSize = textSize
            chordPaint.textSize = textSize
            labelPaint.textSize = textSize
            if (measureHeight(blocks, titlePaint, bodyPaint, labelPaint, draft) <= PAGE_HEIGHT - MARGIN * 2) {
                break
            }
            textSize -= 1f
        }

        var y = MARGIN + titlePaint.fontSpacing
        y = drawHeader(canvas, draft, titlePaint, y)
        y += bodyPaint.fontSpacing * 0.5f

        blocks.forEach { block ->
            if (block.label.isNotEmpty()) {
                canvas.drawText(block.label, MARGIN, y, labelPaint)
                y += labelPaint.fontSpacing
            }
            block.lines.forEach { line ->
                drawLine(canvas, line, MARGIN, y, bodyPaint, chordPaint)
                y += bodyPaint.fontSpacing
            }
            y += bodyPaint.fontSpacing * 0.35f
        }

        document.finishPage(page)
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    private data class Block(val label: String, val lines: List<String>)

    private fun buildBlocks(draft: ChartDraft): List<Block> =
        draft.sections.map { Block(it.label, it.lines) }

    private fun drawHeader(canvas: Canvas, draft: ChartDraft, paint: Paint, y: Float): Float {
        val keyPart = draft.key?.let { " ($it)" }.orEmpty()
        val artistPart = draft.artist?.let { " — $it" }.orEmpty()
        canvas.drawText("${draft.title}$keyPart$artistPart", MARGIN, y, paint)
        var nextY = y + paint.fontSpacing
        val meta = draft.notes.orEmpty()
        if (meta.isNotEmpty()) {
            paint.textSize = min(paint.textSize, 12f)
            canvas.drawText(meta, MARGIN, nextY, paint)
            nextY += paint.fontSpacing
        }
        return nextY
    }

    private fun measureHeight(
        blocks: List<Block>,
        titlePaint: Paint,
        bodyPaint: Paint,
        labelPaint: Paint,
        draft: ChartDraft,
    ): Float {
        var height = titlePaint.fontSpacing * 2
        draft.notes?.takeIf { it.isNotEmpty() }?.let { height += 12f }
        blocks.forEach { block ->
            if (block.label.isNotEmpty()) height += labelPaint.fontSpacing
            height += block.lines.size * bodyPaint.fontSpacing
            height += bodyPaint.fontSpacing * 0.35f
        }
        return height
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
