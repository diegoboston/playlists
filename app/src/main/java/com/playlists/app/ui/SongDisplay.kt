package com.playlists.app.ui

import com.playlists.app.data.Song

object SongDisplay {
    private const val PREVIEW_LEN = 20

    fun preview(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= PREVIEW_LEN) trimmed else trimmed.take(PREVIEW_LEN) + "…"
    }

    fun playlistLine(keySignature: String, notes: String): String {
        val key = keySignature.trim()
        val note = preview(notes)
        return when {
            key.isNotEmpty() && note.isNotEmpty() -> "($key) $note"
            key.isNotEmpty() -> "($key)"
            note.isNotEmpty() -> note
            else -> ""
        }
    }

    fun subtitle(song: Song, pageCount: Int? = null): String {
        val base = "Key: ${preview(song.keySignature)} · ${preview(song.notes)}"
        return if (pageCount != null) "$base · $pageCount pg" else base
    }

    fun typeBadge(song: Song, pageCount: Int? = null): String =
        if (pageCount != null) "${song.fileType}\n$pageCount pg" else song.fileType
}
