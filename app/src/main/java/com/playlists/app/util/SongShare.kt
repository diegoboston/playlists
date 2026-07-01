package com.playlists.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.playlists.app.R
import com.playlists.app.data.FileType
import java.io.File

object SongShare {
    fun share(context: Context, file: File, title: String, fileType: FileType) {
        if (!file.exists()) return
        val mime = mimeType(file, fileType)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_song)),
        )
    }

    private fun mimeType(file: File, fileType: FileType): String = when (fileType) {
        FileType.PDF -> "application/pdf"
        FileType.IMAGE -> when (file.extension.lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }
}
