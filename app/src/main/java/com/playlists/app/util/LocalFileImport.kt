package com.playlists.app.util

import android.content.Context
import android.net.Uri
import com.playlists.app.ai.OpenAiClient

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
