package com.playlists.app.util

import java.io.File

/**
 * Song [filePath] values in the database are stored relative to shared storage, e.g.
 * `Music/StageManager/songs/Amazing_Grace-42.pdf` — never `/sdcard/...` or
 * `/storage/emulated/0/...`.
 */
object SongStoragePaths {
    const val SONGS_RELATIVE_DIR = "Music/StageManager/songs"

    fun toStoredPath(file: File): String {
        val name = file.name.trim()
        require(name.isNotEmpty()) { "Song file has no name: ${file.absolutePath}" }
        return "$SONGS_RELATIVE_DIR/$name"
    }

    fun normalizeStoredPath(path: String): String {
        val trimmed = path.trim().replace('\\', '/').trimStart('/')
        if (trimmed.isEmpty()) return trimmed
        if (isRelativeSongPath(trimmed)) return trimmed
        val name = File(path).name
        require(name.isNotEmpty()) { "Song path has no filename: $path" }
        return "$SONGS_RELATIVE_DIR/$name"
    }

    fun isRelativeSongPath(path: String): Boolean {
        val norm = path.trim().replace('\\', '/').trimStart('/')
        return norm.startsWith("$SONGS_RELATIVE_DIR/")
    }

    fun fileName(stored: String): String {
        val norm = stored.trim().replace('\\', '/').trimStart('/')
        return when {
            norm.startsWith("$SONGS_RELATIVE_DIR/") ->
                norm.removePrefix("$SONGS_RELATIVE_DIR/").substringAfterLast('/')
            norm.startsWith("/") -> File(stored).name
            else -> norm.substringAfterLast('/')
        }
    }

    fun resolve(stored: String): File {
        val name = fileName(stored)
        return File(StageManagerStorage.songsDir(), name)
    }
}
