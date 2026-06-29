package com.playlists.app.util

import com.playlists.app.data.Song
import com.playlists.app.data.SongDao
import java.io.File

/**
 * Keeps song media (and chart sidecars) at `{title}-{songId}.{ext}` and removes
 * leftover UUID-named files. Idempotent — safe to run on every app start.
 */
object SongFileMigration {
    suspend fun sync(songDao: SongDao) {
        migrate(songDao)
        deleteOrphanUuidFiles(songDao)
    }

    internal suspend fun migrate(songDao: SongDao) {
        val songs = songDao.getAll()
        val groups = songs.groupBy { SongStoragePaths.resolve(it.filePath).absolutePath }
        for ((absolutePath, group) in groups) {
            val source = File(absolutePath)
            if (!source.isFile) continue
            if (group.size == 1) {
                migrateSong(songDao, group.single(), source)
            } else {
                migrateSharedSource(songDao, group, source)
            }
        }
    }

    private suspend fun migrateSong(songDao: SongDao, song: Song, source: File) {
        val target = SongFileOps.canonicalMediaFile(song)
        if (source.absolutePath == target.absolutePath) return
        if (SongFileOps.renameMediaAndSidecar(source, target, song.filePath)) {
            songDao.update(song.copy(filePath = SongStoragePaths.toStoredPath(target)))
        }
    }

    private suspend fun migrateSharedSource(songDao: SongDao, songs: List<Song>, source: File) {
        val fromStored = songs.first().filePath
        var allCopied = true
        songs.forEach { song ->
            val target = SongFileOps.canonicalMediaFile(song)
            if (source.absolutePath == target.absolutePath) {
                songDao.update(song.copy(filePath = SongStoragePaths.toStoredPath(target)))
                return@forEach
            }
            if (SongFileOps.copyMediaAndSidecar(source, target, fromStored)) {
                songDao.update(song.copy(filePath = SongStoragePaths.toStoredPath(target)))
            } else {
                allCopied = false
            }
        }
        if (allCopied && source.isFile) {
            SongFileOps.deleteOrphanSidecar(fromStored)
            source.delete()
        }
    }

    internal suspend fun deleteOrphanUuidFiles(songDao: SongDao) {
        val referenced = songDao.getAll()
            .map { SongStoragePaths.fileName(it.filePath) }
            .toSet()
        val songsDir = StageManagerStorage.songsDir()
        songsDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            if (!SongFileNames.isUuidFileName(file.name)) return@forEach
            if (file.name in referenced) return@forEach
            SongFileOps.deleteOrphanSidecar(
                "${SongStoragePaths.SONGS_RELATIVE_DIR}/${file.name}",
            )
            file.delete()
        }
    }
}
