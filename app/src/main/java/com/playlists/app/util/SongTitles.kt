package com.playlists.app.util

object SongTitles {
    fun fromFilename(filename: String): String =
        SongTitleMigration.parse(filename).title
}
