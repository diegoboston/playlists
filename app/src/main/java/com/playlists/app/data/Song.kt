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
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val lastViewedAt: Long? = null,
) {
    fun isAiLyrics(): Boolean = isAiLyricsNotes(notes)

    companion object {
        const val NOTES_AI_LYRICS = "AI lyrics"
        const val NOTES_AI_CHART = "AI chart"

        fun isAiLyricsNotes(notes: String): Boolean =
            notes.substringBefore(" · ").equals(NOTES_AI_LYRICS, ignoreCase = true)
    }
}

enum class FileType {
    IMAGE,
    PDF,
}
