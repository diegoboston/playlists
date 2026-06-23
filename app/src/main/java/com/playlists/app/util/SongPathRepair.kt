package com.playlists.app.util

import com.playlists.app.data.Song
import java.io.File

/**
 * Reconciles [Song.filePath] with files on disk. Stored paths are relative under
 * [SongStoragePaths.SONGS_RELATIVE_DIR]. When the resolved file is missing, locate
 * the file in [songsDir] and return an updated stored path.
 */
object SongPathRepair {
    private val ID_SUFFIX = Regex("""-(\d+)\.[^.]+$""")

    fun repairPath(song: Song, songsDir: File): String? {
        if (!songsDir.isDirectory) return null
        if (fileInDir(songsDir, song.filePath).isFile) {
            return normalizeIfNeeded(song.filePath)
        }

        val baseName = SongStoragePaths.fileName(song.filePath)
        if (baseName.isNotBlank()) {
            val inDir = File(songsDir, baseName)
            if (inDir.isFile) return SongStoragePaths.toStoredPath(inDir)
        }

        val byId = songsDir.listFiles()
            ?.filter { file -> file.isFile && fileSongId(file.name) == song.id }
            ?: emptyList()
        val found = when (byId.size) {
            0 -> null
            1 -> byId[0]
            else -> byId.firstOrNull { SongFileNaming.matches(song.title, song.keySignature, song.id, it) }
                ?: byId.first()
        } ?: return null
        return SongStoragePaths.toStoredPath(found)
    }

    fun normalizeIfNeeded(stored: String): String? {
        val normalized = SongStoragePaths.normalizeStoredPath(stored)
        return if (normalized == stored.trim().replace('\\', '/').trimStart('/')) null else normalized
    }

    private fun fileSongId(fileName: String): Long? {
        val match = ID_SUFFIX.find(fileName) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private fun fileInDir(songsDir: File, stored: String): File =
        File(songsDir, SongStoragePaths.fileName(stored))
}
