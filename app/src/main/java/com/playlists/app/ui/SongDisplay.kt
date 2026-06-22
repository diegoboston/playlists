package com.playlists.app.ui

import com.playlists.app.data.Song

object SongDisplay {
    private const val PREVIEW_LEN = 20

    fun preview(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= PREVIEW_LEN) trimmed else trimmed.take(PREVIEW_LEN) + "…"
    }

    fun keySuffix(keySignature: String): String {
        val key = keySignature.trim()
        return if (key.isNotEmpty()) " ($key)" else ""
    }

    fun titleWithKey(title: String, keySignature: String): String =
        title + keySuffix(keySignature)

    fun notesLine(notes: String): String = preview(notes)

    fun playlistLine(keySignature: String, notes: String): String {
        val note = preview(notes)
        return note
    }

    fun subtitle(song: Song, pageCount: Int? = null): String {
        val note = preview(song.notes)
        return note
    }

    fun typeBadge(song: Song, pageCount: Int? = null): String =
        if (pageCount != null) "${song.fileType}\n$pageCount pg" else song.fileType
}
