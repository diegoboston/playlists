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
    private val UUID_FILE = Regex(
        """^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.[^.]+$""",
        RegexOption.IGNORE_CASE,
    )

    fun repairAll(songs: List<Song>, songsDir: File): Map<Long, String> {
        if (!songsDir.isDirectory) return emptyMap()
        val updates = linkedMapOf<Long, String>()
        for (song in songs.sortedBy { it.id }) {
            val current = updates[song.id] ?: song.filePath
            val repaired = repairPath(song.copy(filePath = current), songsDir)
                ?: normalizeIfNeeded(current)
            if (repaired != null) updates[song.id] = repaired
        }
        assignUnreferencedFiles(songs, songsDir, updates)
        return updates.filter { (id, path) -> songs.first { it.id == id }.filePath != path }
    }

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
        val current = stored.trim().replace('\\', '/').trimStart('/')
        return if (normalized == current) null else normalized
    }

    private fun assignUnreferencedFiles(
        songs: List<Song>,
        songsDir: File,
        updates: MutableMap<Long, String>,
    ) {
        val patched = songs.map { song ->
            val path = updates[song.id] ?: song.filePath
            song.copy(filePath = path)
        }
        val referenced = patched.map { SongStoragePaths.fileName(it.filePath) }.toMutableSet()
        val onDisk = songsDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: return
        val available = onDisk.filter { it.name !in referenced }.toMutableList()
        val unmatched = patched
            .filter { !fileInDir(songsDir, updates[it.id] ?: it.filePath).isFile }
            .sortedBy { it.id }

        for (song in unmatched) {
            val candidates = available.filter { file ->
                expectedExtensions(song.fileType).contains(file.extension.lowercase())
            }
            val pick = pickCandidate(song, candidates) ?: continue
            updates[song.id] = SongStoragePaths.toStoredPath(pick)
            referenced.add(pick.name)
            available.remove(pick)
        }
    }

    private fun pickCandidate(song: Song, candidates: List<File>): File? = when {
        candidates.isEmpty() -> null
        candidates.size == 1 -> candidates[0]
        song.isPlaceholder -> candidates.firstOrNull { UUID_FILE.matches(it.name) }
            ?: candidates.minByOrNull { it.name }
        else -> candidates.firstOrNull { fileSongId(it.name) == song.id }
            ?: candidates.minByOrNull { it.name }
    }

    private fun expectedExtensions(fileType: String): Set<String> = when (fileType.uppercase()) {
        "PDF" -> setOf("pdf")
        else -> setOf("png", "jpg", "jpeg", "webp", "gif")
    }

    internal fun fileSongId(fileName: String): Long? {
        val match = ID_SUFFIX.find(fileName) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private fun fileInDir(songsDir: File, stored: String): File =
        File(songsDir, SongStoragePaths.fileName(stored))
}
