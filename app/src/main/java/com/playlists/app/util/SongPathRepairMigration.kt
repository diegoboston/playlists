package com.playlists.app.util

import android.content.Context
import android.util.Log
import com.playlists.app.data.SongRepository
import java.io.File

object SongPathRepairMigration {
    private const val TAG = "SongPathRepairMigration"
    private const val MARKER_NAME = ".song_paths_relative"
    private const val MARKER_VERSION = "v2"

    fun markerFile(): File = File(StageManagerStorage.root(), MARKER_NAME)

    suspend fun runIfNeeded(context: Context, songRepository: SongRepository) {
        if (!StageManagerStorage.hasAccess(context)) return
        val repaired = songRepository.repairAllFilePaths(StageManagerStorage.songsDir())
        val marker = markerFile()
        val needsOrphanRescan = !marker.isFile || marker.readText().trim() != MARKER_VERSION
        if (needsOrphanRescan) {
            OrphanSongFilesMigration.clearPrompted()
            marker.writeText(MARKER_VERSION)
            Log.i(TAG, "Repaired $repaired song file path(s); orphan scan will rerun")
        } else if (repaired > 0) {
            Log.i(TAG, "Repaired $repaired song file path(s)")
        }
    }
}
