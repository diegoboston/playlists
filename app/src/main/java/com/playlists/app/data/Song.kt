package com.playlists.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val keySignature: String,
    val notes: String,
    val filePath: String,
    val fileType: String,
    val mimeType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val sortOrder: Int = 0,
    val lastViewedAt: Long? = null,
    val isPlaceholder: Boolean = false,
)

enum class FileType {
    IMAGE,
    PDF,
}
