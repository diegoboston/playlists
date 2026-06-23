package com.playlists.app.util

import java.io.File

object OrphanSongFiles {
    fun findOrphans(songsDir: File, storedPaths: Collection<String>): List<File> {
        if (!songsDir.isDirectory) return emptyList()
        val referencedNames = storedPaths
            .map { SongStoragePaths.fileName(it) }
            .filter { it.isNotBlank() }
            .toSet()
        return songsDir.listFiles()
            ?.filter { file -> file.isFile && file.name !in referencedNames }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun deleteFiles(files: Collection<File>): Int =
        files.count { it.isFile && it.delete() }
}
