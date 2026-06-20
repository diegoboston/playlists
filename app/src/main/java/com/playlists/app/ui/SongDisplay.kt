package com.playlists.app.ui

import com.playlists.app.data.Song

object SongDisplay {
    private const val PREVIEW_LEN = 20

    fun preview(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= PREVIEW_LEN) trimmed else trimmed.take(PREVIEW_LEN) + "…"
    }

    fun subtitle(song: Song): String =
        "Key: ${preview(song.keySignature)} · ${preview(song.notes)}"
}
