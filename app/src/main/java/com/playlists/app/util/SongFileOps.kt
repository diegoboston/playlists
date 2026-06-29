package com.playlists.app.util

import com.playlists.app.data.Song
import java.io.File

internal object SongFileOps {
    fun canonicalMediaFile(song: Song): File {
        val currentName = SongStoragePaths.fileName(song.filePath)
        val ext = SongFileNames.extensionOf(currentName)
        val name = SongFileNames.mediaFileName(song.title, song.id, ext)
        return File(StageManagerStorage.songsDir(), name)
    }

    fun renameMediaAndSidecar(from: File, to: File, fromStoredPath: String): Boolean {
        if (from.absolutePath == to.absolutePath) return true
        val chartFrom = ChartDraftStore.resolveChartFile(fromStoredPath)
        if (!from.renameTo(to)) return false
        if (chartFrom.isFile) {
            val toStored = SongStoragePaths.toStoredPath(to)
            chartFrom.renameTo(ChartDraftStore.resolveChartFile(toStored))
        }
        return true
    }

    fun copyMediaAndSidecar(from: File, to: File, fromStoredPath: String): Boolean {
        if (from.absolutePath == to.absolutePath) return true
        if (!from.isFile) return false
        from.copyTo(to, overwrite = true)
        val chartFrom = ChartDraftStore.resolveChartFile(fromStoredPath)
        if (chartFrom.isFile) {
            val toStored = SongStoragePaths.toStoredPath(to)
            chartFrom.copyTo(ChartDraftStore.resolveChartFile(toStored), overwrite = true)
        }
        return true
    }

    fun deleteOrphanSidecar(storedPath: String) {
        ChartDraftStore.deleteIfPresent(storedPath)
    }
}
