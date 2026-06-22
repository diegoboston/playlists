package com.playlists.app.util

import android.os.Parcelable
import com.playlists.app.data.FileType
import java.io.File
import kotlinx.parcelize.Parcelize

@Parcelize
data class PendingImport(
    val filePath: String,
    val fileType: FileType,
    val mimeType: String,
    val suggestedTitle: String,
    val suggestedKey: String = "",
    val suggestedNotes: String = "",
) : Parcelable {
    val file: File get() = File(filePath)

    companion object {
        fun fromRawTitle(file: File, fileType: FileType, mimeType: String, rawTitle: String): PendingImport {
            val parsed = SongTitles.parseFilename(rawTitle)
            return PendingImport(
                filePath = file.absolutePath,
                fileType = fileType,
                mimeType = mimeType,
                suggestedTitle = parsed.title,
                suggestedKey = parsed.keySignature,
                suggestedNotes = parsed.notes,
            )
        }
    }
}
