package com.playlists.app.util

import android.content.Context
import android.util.Log
import com.playlists.app.data.SongRepository
import java.io.File

object SongFilenameMigration {
    private const val TAG = "SongFilenameMigration"
    private const val MARKER_NAME = ".song_filenames_migrated"

    suspend fun runIfNeeded(context: Context, repository: SongRepository) {
        if (!StageManagerStorage.hasAccess(context)) return
        val marker = File(StageManagerStorage.root(), MARKER_NAME)
        if (marker.isFile) return
        val migrated = repository.migrateAllFilenames()
        marker.writeText("v1")
        Log.i(TAG, "Renamed $migrated song file(s) to Title-Key-Id convention")
    }
}
