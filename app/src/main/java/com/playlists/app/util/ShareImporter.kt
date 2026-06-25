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
                SharePayload.FileImport(importFromUri(context, uri, type) ?: return null)
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
        return SharePayload.FileImport(importFromUri(context, uri, type) ?: return null)
    }

    private fun importFromUri(context: Context, uri: Uri, mimeType: String): PendingImport? {
        val resolver = context.contentResolver
        val ext = FileStorage.extensionForMime(mimeType)
        val file = resolver.openInputStream(uri)?.use { stream ->
            FileStorage.storeStream(stream, ext)
        } ?: return null
        val fileType = if (mimeType.contains("pdf")) FileType.PDF else FileType.IMAGE
        val rawTitle = rawTitleFromUri(resolver, uri, file)
        return PendingImport.fromRawTitle(file, fileType, rawTitle)
    }

    private fun rawTitleFromUri(resolver: ContentResolver, uri: Uri, file: File): String {
        val displayName = resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return displayName ?: file.name
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
