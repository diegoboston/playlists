package com.playlists.app.util

import android.content.Context
import com.playlists.app.data.SongRepository
import java.io.File

object OrphanSongFilesMigration {
    private const val MARKER_NAME = ".orphan_songs_prompted"

    fun markerFile(): File = File(StageManagerStorage.root(), MARKER_NAME)

    fun hasBeenPrompted(): Boolean = markerFile().isFile

    fun markPrompted() {
        markerFile().writeText("v1")
    }

    fun clearPrompted() {
        markerFile().delete()
    }

    suspend fun findOrphansIfNeeded(
        context: Context,
        songRepository: SongRepository,
    ): List<File>? {
        if (!StageManagerStorage.hasAccess(context)) return null
        if (hasBeenPrompted()) return null
        val referenced = songRepository.getAll().map { it.filePath }
        val orphans = OrphanSongFiles.findOrphans(StageManagerStorage.songsDir(), referenced)
        return orphans.ifEmpty {
            markPrompted()
            null
        }
    }
}
