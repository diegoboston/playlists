package com.playlists.app.render

import android.graphics.Bitmap
import com.playlists.app.data.FileType
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.ui.PdfHelper
import com.playlists.app.ui.PlaybackFrame
import com.playlists.app.ui.buildPlaybackFrames
import com.playlists.app.ui.buildTocEntries
import com.playlists.app.ui.countMissingPlaylistFiles
import com.playlists.app.ui.sanitizeExportFilename
import com.playlists.app.util.SongStoragePaths
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.util.UUID
import kotlin.math.min

object PlaylistPdfExporter {
    data class Result(
        val file: File,
        val tocPages: Int,
        val bodyPages: Int,
        val skippedMissing: Int,
    )

    fun export(
        cacheDir: File,
        playlistName: String,
        entries: List<PlaylistSongWithDetails>,
    ): Result {
        require(entries.isNotEmpty()) { "Playlist is empty" }

        val tocFile = File(cacheDir, "export-toc-${UUID.randomUUID()}.pdf")
        val document = PDDocument()
        try {
            val tocPages = PlaylistTocRenderer.renderToFile(tocFile, playlistName, buildTocEntries(entries))
            appendPagesFromFile(document, tocFile)

            val frames = buildPlaybackFrames(entries)
            frames.forEach { frame -> appendMediaPage(document, frame) }

            val exportsDir = File(cacheDir, "exports").also { it.mkdirs() }
            val file = File(exportsDir, "${sanitizeExportFilename(playlistName)}.pdf")
            document.save(file)

            return Result(
                file = file,
                tocPages = tocPages,
                bodyPages = frames.size,
                skippedMissing = countMissingPlaylistFiles(entries),
            )
        } finally {
            document.close()
            tocFile.delete()
        }
    }

    private fun appendPagesFromFile(destination: PDDocument, sourceFile: File) {
        PDDocument.load(sourceFile).use { source ->
            for (pageIndex in 0 until source.numberOfPages) {
                destination.importPage(source.getPage(pageIndex))
            }
        }
    }

    private fun appendMediaPage(document: PDDocument, frame: PlaybackFrame) {
        val file = SongStoragePaths.resolve(frame.entry.filePath)
        val fileType = runCatching { FileType.valueOf(frame.entry.fileType) }
            .getOrDefault(FileType.IMAGE)
        when (fileType) {
            FileType.PDF -> {
                if (!appendPdfPageVector(document, file, frame.pageIndex)) {
                    appendPdfPageRaster(document, file, frame.pageIndex)
                }
            }
            FileType.IMAGE -> appendImagePage(document, file)
        }
    }

    private fun appendPdfPageVector(document: PDDocument, file: File, pageIndex: Int): Boolean {
        return try {
            PDDocument.load(file).use { source ->
                if (pageIndex !in 0 until source.numberOfPages) return false
                document.importPage(source.getPage(pageIndex))
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun appendPdfPageRaster(document: PDDocument, file: File, pageIndex: Int) {
        val bitmap = PdfHelper.renderPage(file, pageIndex, PdfPageSpec.MEDIA_RENDER_WIDTH) ?: return
        try {
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            val image = LosslessFactory.createFromImage(document, bitmap)
            drawImageFit(document, page, image, bitmap.width.toFloat(), bitmap.height.toFloat())
        } finally {
            bitmap.recycle()
        }
    }

    private fun appendImagePage(document: PDDocument, file: File) {
        val page = PDPage(PDRectangle.LETTER)
        document.addPage(page)
        val image = PDImageXObject.createFromFileByExtension(file, document)
        drawImageFit(document, page, image, image.width.toFloat(), image.height.toFloat())
    }

    private fun drawImageFit(
        document: PDDocument,
        page: PDPage,
        image: PDImageXObject,
        imageWidth: Float,
        imageHeight: Float,
    ) {
        val pageBox = page.mediaBox
        val scale = min(pageBox.width / imageWidth, pageBox.height / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        val x = (pageBox.width - width) / 2f
        val y = (pageBox.height - height) / 2f
        PDPageContentStream(document, page).use { stream ->
            stream.drawImage(image, x, y, width, height)
        }
    }
}
