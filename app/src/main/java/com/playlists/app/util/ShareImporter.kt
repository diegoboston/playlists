package com.playlists.app.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import androidx.core.content.IntentCompat
import com.playlists.app.data.FileType
import com.playlists.app.data.Song
import com.playlists.app.data.SongRepository
import java.io.File

object ShareImporter {
    private val URL_IN_TEXT = Regex("""https?://[^\s<>"']+""")

    fun parseIntent(context: Context, intent: Intent): SharePayload? {
        return when (intent.action) {
            Intent.ACTION_SEND -> parseSend(context, intent)
            Intent.ACTION_VIEW -> parseView(context, intent)
            else -> null
        }
    }

    fun titleHintFromUrl(url: String): String =
        url.substringAfterLast('/').substringBefore('?').replace('_', ' ').trim()
            .ifBlank { "Shared chart" }

    fun extractUrl(text: String): String? {
        val match = URL_IN_TEXT.find(text.trim()) ?: return null
        return match.value.trimEnd('.', ',', ';', ')', ']', '"', '\'')
    }

    private fun parseSend(context: Context, intent: Intent): SharePayload? {
        val type = intent.type ?: return null
        return when {
            type.startsWith("text/") -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                if (text.isEmpty()) return null
                val url = extractUrl(text) ?: return null
                if (!URLUtil.isNetworkUrl(url)) return null
                SharePayload.ChartUrl(url, titleHintFromUrl(url))
            }
            type.startsWith("image/") || type == "application/pdf" -> {
                val uri = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java,
                ) ?: return null
                SharePayload.FileImport(importFromUri(context, uri, mimeType = type) ?: return null)
            }
            else -> null
        }
    }

    private fun parseView(context: Context, intent: Intent): SharePayload? {
        val uri = intent.data ?: return null
        val url = uri.toString()
        if (URLUtil.isNetworkUrl(url)) {
            return SharePayload.ChartUrl(url, titleHintFromUrl(url))
        }
        val type = intent.type ?: context.contentResolver.getType(uri) ?: return null
        return SharePayload.FileImport(importFromUri(context, uri, mimeType = type) ?: return null)
    }

    /**
     * Copy a content [uri] into song storage. When [useFilenameHints] is false (scan-from-camera),
     * title/key/notes stay empty so OCR or the user can fill them.
     */
    fun importFromUri(
        context: Context,
        uri: Uri,
        mimeType: String? = null,
        useFilenameHints: Boolean = true,
    ): PendingImport? {
        val resolver = context.contentResolver
        val resolvedMime = mimeType?.takeIf { it.isNotBlank() && it != "*/*" }
            ?: resolver.getType(uri)
            ?: mimeFromDisplayName(rawTitleFromUri(resolver, uri, fallbackName = null))
            ?: return null
        val fileType = fileTypeForMime(resolvedMime) ?: return null
        val ext = FileStorage.extensionForMime(resolvedMime)
        val file = resolver.openInputStream(uri)?.use { stream ->
            FileStorage.storeStream(stream, ext)
        } ?: return null
        if (!useFilenameHints) {
            return PendingImport(
                filePath = file.absolutePath,
                fileType = fileType,
                suggestedTitle = "",
            )
        }
        val rawTitle = rawTitleFromUri(resolver, uri, fallbackName = file.name)
        return PendingImport.fromRawTitle(file, fileType, rawTitle)
    }

    fun fileTypeForMime(mimeType: String): FileType? {
        val mime = mimeType.lowercase()
        return when {
            mime.contains("pdf") -> FileType.PDF
            mime.startsWith("image/") -> FileType.IMAGE
            else -> null
        }
    }

    internal fun mimeFromDisplayName(name: String?): String? {
        val lower = name?.substringAfterLast('/')?.lowercase() ?: return null
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            else -> null
        }
    }

    private fun rawTitleFromUri(resolver: ContentResolver, uri: Uri, fallbackName: String?): String {
        val displayName = resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return displayName ?: fallbackName.orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }
    }

    suspend fun saveSong(
        repository: SongRepository,
        pending: PendingImport,
        title: String,
        keySignature: String,
        notes: String,
    ): Long {
        return repository.insert(
            Song(
                title = title.trim().ifBlank { pending.suggestedTitle },
                keySignature = keySignature.trim().ifBlank { pending.suggestedKey },
                notes = notes.trim().ifBlank { pending.suggestedNotes },
                filePath = SongStoragePaths.toStoredPath(pending.file),
                fileType = pending.fileType.name,
            ),
        )
    }
}
