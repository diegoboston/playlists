package com.playlists.app.util

import android.content.Context
import android.util.Log
import com.playlists.app.R
import com.playlists.app.data.Song
import com.playlists.app.data.SongDao

/**
 * Read-only check that every song row resolves to a file on disk and that no two rows
 * share the same stored path. Run after [SongFileMigration.sync] so shared existing files
 * are split first; remaining shared paths usually mean a missing file.
 */
object SongFileIntegrity {
    data class MissingSong(
        val songId: Long,
        val title: String,
        val filePath: String,
    )

    data class SharedFilePath(
        val filePath: String,
        val songIds: List<Long>,
        val titles: List<String>,
        val fileExists: Boolean,
    )

    data class ScanResult(
        val missingSongs: List<MissingSong>,
        val sharedFilePaths: List<SharedFilePath>,
    ) {
        val hasIssues: Boolean
            get() = missingSongs.isNotEmpty() || sharedFilePaths.isNotEmpty()
    }

    suspend fun scan(songDao: SongDao): ScanResult = scan(songDao.getAll())

    fun scan(songs: List<Song>): ScanResult {
        val missingSongs = mutableListOf<MissingSong>()
        val byStoredPath = linkedMapOf<String, MutableList<Song>>()

        for (song in songs) {
            val storedPath = SongStoragePaths.normalizeStoredPath(song.filePath)
            byStoredPath.getOrPut(storedPath) { mutableListOf() }.add(song)
            if (!SongStoragePaths.resolve(storedPath).isFile) {
                missingSongs.add(
                    MissingSong(
                        songId = song.id,
                        title = song.title,
                        filePath = storedPath,
                    ),
                )
            }
        }

        val sharedFilePaths = byStoredPath
            .filter { (_, group) -> group.size > 1 }
            .map { (storedPath, group) ->
                SharedFilePath(
                    filePath = storedPath,
                    songIds = group.map { it.id },
                    titles = group.map { it.title },
                    fileExists = SongStoragePaths.resolve(storedPath).isFile,
                )
            }

        return ScanResult(
            missingSongs = missingSongs.sortedBy { it.songId },
            sharedFilePaths = sharedFilePaths.sortedBy { it.filePath },
        )
    }

    fun log(tag: String, result: ScanResult, songCount: Int) {
        if (!result.hasIssues) {
            Log.i(tag, "Song file integrity OK ($songCount songs)")
            return
        }
        if (result.missingSongs.isNotEmpty()) {
            Log.w(
                tag,
                "Song file integrity: ${result.missingSongs.size} missing file(s)",
            )
            result.missingSongs.forEach { entry ->
                Log.w(
                    tag,
                    "  missing songId=${entry.songId} path=${entry.filePath} title=${entry.title}",
                )
            }
        }
        if (result.sharedFilePaths.isNotEmpty()) {
            Log.w(
                tag,
                "Song file integrity: ${result.sharedFilePaths.size} shared file path(s)",
            )
            result.sharedFilePaths.forEach { group ->
                Log.w(
                    tag,
                    "  shared path=${group.filePath} exists=${group.fileExists} " +
                        "songIds=${group.songIds} titles=${group.titles}",
                )
            }
        }
    }

    fun snackbarMessage(context: Context, result: ScanResult): String {
        val parts = buildList {
            if (result.missingSongs.isNotEmpty()) {
                add(
                    context.resources.getQuantityString(
                        R.plurals.song_integrity_missing,
                        result.missingSongs.size,
                        result.missingSongs.size,
                    ),
                )
            }
            if (result.sharedFilePaths.isNotEmpty()) {
                add(
                    context.resources.getQuantityString(
                        R.plurals.song_integrity_shared,
                        result.sharedFilePaths.size,
                        result.sharedFilePaths.size,
                    ),
                )
            }
        }
        return context.getString(R.string.song_integrity_snackbar, parts.joinToString(", "))
    }
}
