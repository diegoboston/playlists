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
) : Parcelable {
    val file: File get() = File(filePath)

    companion object {
        fun from(file: File, fileType: FileType, mimeType: String, suggestedTitle: String) =
            PendingImport(
                filePath = file.absolutePath,
                fileType = fileType,
                mimeType = mimeType,
                suggestedTitle = suggestedTitle,
            )
    }
}
