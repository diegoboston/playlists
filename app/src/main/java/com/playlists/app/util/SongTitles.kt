package com.playlists.app.util

object SongTitles {
    fun fromFilename(filename: String): String =
        filename.substringBeforeLast('.').replace('_', ' ')
}
