package com.playlists.app.util

object SongTitles {
    fun parseFilename(filename: String): SongTitleMigration.Result =
        SongTitleMigration.parse(filename)

    fun fromFilename(filename: String): String =
        parseFilename(filename).title
}
