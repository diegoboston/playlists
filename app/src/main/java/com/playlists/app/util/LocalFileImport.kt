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
        val stored = storeCapturedPage(file) ?: return ScanImportOutcome(pending = null)
        return finishStoredCapture(context, stored)
    }

    fun addCapturedPage(captureFile: File, existing: PendingImport): ScanImportOutcome {
        val stored = storeCapturedPage(captureFile) ?: return ScanImportOutcome(pending = null)
        return addStoredPage(stored, existing)
    }

    fun beginCapturedPage(
        context: Context,
        captureFile: File,
        existing: PendingImport? = null,
    ): CapturePageStart {
        val stored = storeCapturedPage(captureFile) ?: return CapturePageStart.Failed
        if (AppPrefs.isAdjustPageEnabled(context)) {
            return CapturePageStart.NeedsAdjust(stored)
        }
        val outcome = if (existing == null) {
            finishStoredCapture(context, stored)
        } else {
            addStoredPage(stored, existing)
        }
        return CapturePageStart.Ready(outcome)
    }

    fun finishStoredCapture(context: Context, stored: File): ScanImportOutcome {
        val pending = PendingImport(
            filePath = stored.absolutePath,
            fileType = FileType.IMAGE,
            suggestedTitle = "",
            allowAddPages = true,
        )
        return finishWithOcr(context, pending)
    }

    fun addStoredPage(stored: File, existing: PendingImport): ScanImportOutcome {
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

    fun storeCapturedPage(captureFile: File): File? {
        if (!CaptureImageStore.isUsableCapture(captureFile)) return null
        return captureFile.inputStream().use { FileStorage.storeStream(it, "jpg") }
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

sealed class CapturePageStart {
    data object Failed : CapturePageStart()
    data class NeedsAdjust(val stored: File) : CapturePageStart()
    data class Ready(val outcome: ScanImportOutcome) : CapturePageStart()
}
