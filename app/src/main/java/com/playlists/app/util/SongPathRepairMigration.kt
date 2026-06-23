package com.playlists.app.util

import android.content.Context
import android.util.Log
import com.playlists.app.data.SongRepository
import java.io.File

object SongPathRepairMigration {
    private const val TAG = "SongPathRepairMigration"
    private const val MARKER_NAME = ".song_paths_relative"

    fun markerFile(): File = File(StageManagerStorage.root(), MARKER_NAME)

    suspend fun runIfNeeded(context: Context, songRepository: SongRepository) {
        if (!StageManagerStorage.hasAccess(context)) return
        if (markerFile().isFile) return
        val repaired = songRepository.repairAllFilePaths(StageManagerStorage.songsDir())
        OrphanSongFilesMigration.clearPrompted()
        markerFile().writeText("v1")
        Log.i(TAG, "Repaired $repaired song file path(s); orphan scan will rerun")
    }
}
