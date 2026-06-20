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
    fun parseIntent(context: Context, intent: Intent): PendingImport? {
        return when (intent.action) {
            Intent.ACTION_SEND -> parseSend(context, intent)
            Intent.ACTION_VIEW -> parseView(context, intent)
            else -> null
        }
    }

    private fun parseSend(context: Context, intent: Intent): PendingImport? {
        val type = intent.type ?: return null
        return when {
            type.startsWith("text/") -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                if (text.isEmpty()) return null
                if (URLUtil.isNetworkUrl(text)) {
                    importFromUrl(context, text)
                } else {
                    null
                }
            }
            type.startsWith("image/") || type == "application/pdf" -> {
                val uri = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java,
                ) ?: return null
                importFromUri(context, uri, type)
            }
            else -> null
        }
    }

    private fun parseView(context: Context, intent: Intent): PendingImport? {
        val uri = intent.data ?: return null
        val type = intent.type ?: context.contentResolver.getType(uri) ?: return null
        return importFromUri(context, uri, type)
    }

    private fun importFromUri(context: Context, uri: Uri, mimeType: String): PendingImport? {
        val resolver = context.contentResolver
        val ext = FileStorage.extensionForMime(mimeType)
        val file = resolver.openInputStream(uri)?.use { stream ->
            FileStorage.storeStream(context, stream, ext)
        } ?: return null
        val fileType = if (mimeType.contains("pdf")) FileType.PDF else FileType.IMAGE
        val title = suggestTitle(resolver, uri, file)
        return PendingImport.from(file, fileType, mimeType, title)
    }

    private fun importFromUrl(context: Context, url: String): PendingImport? {
        val (bytes, mime) = FileStorage.downloadUrl(url) ?: return null
        val ext = FileStorage.extensionForMime(mime)
        val file = FileStorage.storeBytes(context, bytes, ext)
        val fileType = if (mime.contains("pdf")) FileType.PDF else FileType.IMAGE
        val title = url.substringAfterLast('/').substringBefore('?').ifBlank { "Shared link" }
        return PendingImport.from(file, fileType, mime, title)
    }

    private fun suggestTitle(resolver: ContentResolver, uri: Uri, file: File): String {
        val displayName = resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return displayName?.substringBeforeLast('.') ?: file.nameWithoutExtension
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
                keySignature = keySignature.trim(),
                notes = notes.trim(),
                filePath = pending.file.absolutePath,
                fileType = pending.fileType.name,
                mimeType = pending.mimeType,
            )
        )
    }
}
