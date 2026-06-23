package com.playlists.app.util

import java.io.File

object OrphanSongFiles {
    fun findOrphans(songsDir: File, referencedPaths: Collection<String>): List<File> {
        if (!songsDir.isDirectory) return emptyList()
        val referenced = referencedPaths
            .mapNotNull { path ->
                runCatching { File(path).canonicalPath }.getOrNull()
            }
            .toSet()
        return songsDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    runCatching { file.canonicalPath }.getOrNull()?.let { it !in referenced } == true
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun deleteFiles(files: Collection<File>): Int =
        files.count { it.isFile && it.delete() }
}
