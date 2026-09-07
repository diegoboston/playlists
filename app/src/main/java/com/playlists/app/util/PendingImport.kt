package com.playlists.app.util

import android.os.Parcelable
import com.playlists.app.data.FileType
import java.io.File
import kotlinx.parcelize.Parcelize

@Parcelize
data class PendingImport(
    val filePath: String,
    val fileType: FileType,
    val suggestedTitle: String,
    val suggestedKey: String = "",
    val suggestedNotes: String = "",
    val extraPagePaths: List<String> = emptyList(),
    val allowAddPages: Boolean = false,
) : Parcelable {
    val file: File get() = File(filePath)

    val allPageFiles: List<File>
        get() = listOf(file) + extraPagePaths.map { File(it) }

    val pageCount: Int get() = 1 + extraPagePaths.size

    companion object {
        fun fromRawTitle(file: File, fileType: FileType, rawTitle: String): PendingImport {
            val parsed = SongTitles.parseFilename(rawTitle)
            return PendingImport(
                filePath = file.absolutePath,
                fileType = fileType,
                suggestedTitle = parsed.title,
                suggestedKey = parsed.keySignature,
                suggestedNotes = parsed.notes,
            )
        }
    }
}
