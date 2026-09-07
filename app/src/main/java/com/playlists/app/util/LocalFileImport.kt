package com.playlists.app.util

import android.content.Context
import android.net.Uri
import com.playlists.app.ai.OpenAiClient
import com.playlists.app.data.FileType
import com.playlists.app.render.ImagePagesPdf
import java.io.File

data class ScanImportOutcome(
    val pending: PendingImport?,
    val ocrAttempted: Boolean = false,
    val ocrFailed: Boolean = false,
)

object LocalFileImport {
    fun fromDocument(context: Context, uri: Uri): PendingImport? =
        ShareImporter.importFromUri(context, uri, useFilenameHints = true)

    fun fromGalleryImage(context: Context, uri: Uri): ScanImportOutcome {
        val pending = ShareImporter.importFromUri(context, uri, useFilenameHints = false)
            ?: return ScanImportOutcome(pending = null)
        return finishWithOcr(context, pending)
    }

    fun fromCapturedImage(context: Context, file: File): ScanImportOutcome {
        val stored = storeCapturedPage(context, file) ?: return ScanImportOutcome(pending = null)
        val pending = PendingImport(
            filePath = stored.absolutePath,
            fileType = FileType.IMAGE,
            suggestedTitle = "",
            allowAddPages = true,
        )
        return finishWithOcr(context, pending)
    }

    fun addCapturedPage(context: Context, captureFile: File, existing: PendingImport): ScanImportOutcome {
        val stored = storeCapturedPage(context, captureFile) ?: return ScanImportOutcome(pending = null)
        val extra = existing.extraPagePaths + stored.absolutePath
        return ScanImportOutcome(
            pending = existing.copy(extraPagePaths = extra, allowAddPages = true),
        )
    }

    fun materializeForSave(pending: PendingImport): PendingImport {
        val pages = pending.allPageFiles.filter { it.exists() && it.length() > 0L }
        if (!ImagePagesPdf.shouldCombine(pages.size)) {
            return pending.copy(extraPagePaths = emptyList())
        }
        val pdf = ImagePagesPdf.write(pages)
        pages.forEach { runCatching { it.delete() } }
        return pending.copy(
            filePath = pdf.absolutePath,
            fileType = FileType.PDF,
            extraPagePaths = emptyList(),
            allowAddPages = false,
        )
    }

    private fun storeCapturedPage(context: Context, captureFile: File): File? {
        if (!CaptureImageStore.isUsableCapture(captureFile)) return null
        val stored = captureFile.inputStream().use { FileStorage.storeStream(it, "jpg") }
        flattenIfReady(context, stored)
        return stored
    }

    private fun flattenIfReady(context: Context, file: File) {
        if (!AiCredentialStore.isOpenAiKeyReady(context)) return
        val apiKey = AiCredentialStore.getOpenAiApiKey(context) ?: return
        runCatching {
            val jpeg = ImportImagePrep.jpegBytesForOcr(file, maxEdge = 2048, quality = 85)
                ?: file.readBytes()
            val cleaned = OpenAiClient(apiKey).flattenAndCleanupImage(jpeg)
            if (!ImportImagePrep.writeJpegFile(file, cleaned)) {
                error("Could not write cleaned image")
            }
        }
    }

    private fun finishWithOcr(context: Context, pending: PendingImport): ScanImportOutcome {
        if (!AiCredentialStore.isOpenAiKeyReady(context)) {
            return ScanImportOutcome(pending = pending)
        }
        val apiKey = AiCredentialStore.getOpenAiApiKey(context)
            ?: return ScanImportOutcome(pending = pending)
        val ocr = runCatching {
            val jpeg = ImportImagePrep.jpegBytesForOcr(pending.file)
                ?: error("Could not encode image for OCR")
            OpenAiClient(apiKey).extractTitleFromImage(jpeg)
        }
        return ScanImportOutcome(
            pending = pending.copy(suggestedTitle = ocr.getOrNull().orEmpty()),
            ocrAttempted = true,
            ocrFailed = ocr.isFailure,
        )
    }
}
