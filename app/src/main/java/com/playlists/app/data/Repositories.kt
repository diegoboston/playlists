package com.playlists.app.data

import android.content.Context
import com.playlists.app.util.FileStorage
import com.playlists.app.util.PlaceholderImageGenerator
import com.playlists.app.util.SongFileNaming
import java.io.File
import kotlinx.coroutines.flow.Flow

class SongRepository(private val songDao: SongDao) {
    fun observeAll(): Flow<List<Song>> = songDao.observeAll()

    suspend fun getAll(): List<Song> = songDao.getAll()

    suspend fun getById(id: Long): Song? = songDao.getById(id)

    suspend fun insert(song: Song): Long {
        val id = songDao.insertAtTop(song)
        val saved = songDao.getById(id) ?: return id
        applyCanonicalFilename(saved)
        return id
    }

    suspend fun update(song: Song) {
        songDao.update(song)
        songDao.getById(song.id)?.let { applyCanonicalFilename(it) }
    }

    suspend fun migrateAllFilenames(): Int {
        val songs = songDao.getAllIncludingDeleted()
        var count = 0
        for (song in songs.sortedBy { it.id }) {
            if (applyCanonicalFilename(song)) count++
        }
        return count
    }

    suspend fun applyCanonicalFilename(song: Song): Boolean {
        val file = File(song.filePath)
        if (!file.isFile) return false
        if (SongFileNaming.matches(song.title, song.keySignature, song.id, file)) return false

        val target = SongFileNaming.resolveTargetFile(file, song.title, song.keySignature, song.id)
        val sharedWithOthers = songDao.getAllIncludingDeleted()
            .any { it.id != song.id && it.filePath == song.filePath }

        val moved = if (sharedWithOthers) {
            file.copyTo(target, overwrite = false)
            target.isFile
        } else {
            file.renameTo(target) || run {
                file.copyTo(target, overwrite = true)
                file.delete()
                target.isFile
            }
        }
        if (!moved) return false

        val updated = song.copy(filePath = target.absolutePath)
        songDao.update(updated)
        return true
    }

    suspend fun getAllIncludingDeleted(): List<Song> = songDao.getAllIncludingDeleted()

    suspend fun isFileSharedWithOtherSongs(songId: Long): Boolean {
        val song = songDao.getById(songId) ?: return false
        return songDao.getAllIncludingDeleted()
            .any { it.id != songId && it.filePath == song.filePath }
    }

    suspend fun delete(id: Long, deleteFile: Boolean = false) {
        val song = songDao.getById(id) ?: return
        songDao.markDeleted(id, System.currentTimeMillis())
        if (!deleteFile) return
        val file = File(song.filePath)
        if (!file.isFile) return
        val stillReferenced = songDao.getAllIncludingDeleted()
            .any { it.id != id && it.filePath == song.filePath }
        if (!stillReferenced) {
            file.delete()
        }
    }

    suspend fun reorder(idsInOrder: List<Long>) = songDao.replaceOrder(idsInOrder)

    suspend fun sortAlpha() {
        val ids = songDao.getAll().sortedBy { it.title.lowercase() }.map { it.id }
        songDao.replaceOrder(ids)
    }

    suspend fun sortByRecentlyAdded() {
        val ids = songDao.getAll()
            .sortedWith(compareByDescending<Song> { it.createdAt }.thenByDescending { it.id })
            .map { it.id }
        songDao.replaceOrder(ids)
    }

    suspend fun sortByRecentlyViewed() {
        val ids = songDao.getAll()
            .sortedWith(
                compareBy<Song> { it.lastViewedAt == null }
                    .thenByDescending { it.lastViewedAt }
                    .thenByDescending { it.id },
            )
            .map { it.id }
        songDao.replaceOrder(ids)
    }

    suspend fun markViewed(id: Long) = songDao.updateLastViewedAt(id, System.currentTimeMillis())

    suspend fun search(query: String): List<Song> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return songDao.getAll()
        return songDao.search(trimmed)
    }

    suspend fun createPlaceholder(
        context: Context,
        title: String,
        keySignature: String = "",
        notes: String = "",
    ): Long {
        val trimmedTitle = title.trim().ifBlank { "Untitled" }
        val bytes = PlaceholderImageGenerator.render(trimmedTitle)
        val stored = FileStorage.storeBytes(context, bytes, "png")
        return insert(
            Song(
                title = trimmedTitle,
                keySignature = keySignature.trim(),
                notes = notes.trim(),
                filePath = stored.absolutePath,
                fileType = FileType.IMAGE.name,
                mimeType = "image/png",
                isPlaceholder = true,
            ),
        )
    }
}

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val playlistSongDao: PlaylistSongDao,
) {
    fun observeAll(): Flow<List<Playlist>> = playlistDao.observeAll()

    suspend fun getAll(): List<Playlist> = playlistDao.getAll()

    suspend fun getById(id: Long): Playlist? = playlistDao.getById(id)

    suspend fun create(name: String): Long =
        playlistDao.insertAtTop(Playlist(name = name))

    suspend fun rename(id: Long, name: String) {
        val playlist = playlistDao.getById(id) ?: return
        playlistDao.update(playlist.copy(name = name))
    }

    suspend fun setColor(id: Long, colorArgb: Int?) {
        val playlist = playlistDao.getById(id) ?: return
        playlistDao.update(playlist.copy(colorArgb = colorArgb))
    }

    suspend fun reorder(idsInOrder: List<Long>) {
        playlistDao.replaceOrder(idsInOrder)
    }

    suspend fun delete(id: Long) = playlistDao.deleteById(id)

    suspend fun duplicate(id: Long, name: String): Long? {
        val source = playlistDao.getById(id) ?: return null
        val entries = playlistSongDao.getForPlaylist(id)
        val newId = playlistDao.insert(
            Playlist(
                name = name,
                sortOrder = playlistDao.maxSortOrder() + 1,
                colorArgb = source.colorArgb,
            )
        )
        entries.forEach { entry ->
            playlistSongDao.insert(
                PlaylistSong(
                    playlistId = newId,
                    songId = entry.songId,
                    position = entry.position,
                )
            )
        }
        return newId
    }

    fun observeSongs(playlistId: Long): Flow<List<PlaylistSongWithDetails>> =
        playlistSongDao.observeForPlaylist(playlistId)

    suspend fun getSongs(playlistId: Long): List<PlaylistSongWithDetails> =
        playlistSongDao.getForPlaylist(playlistId)

    suspend fun addSong(playlistId: Long, songId: Long) {
        val next = playlistSongDao.maxPosition(playlistId) + 1
        playlistSongDao.insert(
            PlaylistSong(
                playlistId = playlistId,
                songId = songId,
                position = next,
            )
        )
    }

    suspend fun removeSong(entryId: Long) = playlistSongDao.deleteById(entryId)

    suspend fun playlistNamesForSong(songId: Long): List<String> =
        playlistSongDao.playlistNamesForSong(songId)

    suspend fun reorder(playlistId: Long, entryIdsInOrder: List<Long>) {
        playlistSongDao.replaceOrder(playlistId, entryIdsInOrder)
    }

    suspend fun setSongs(playlistId: Long, songIds: List<Long>) {
        playlistSongDao.deleteAllForPlaylist(playlistId)
        songIds.forEachIndexed { index, songId ->
            playlistSongDao.insert(
                PlaylistSong(
                    playlistId = playlistId,
                    songId = songId,
                    position = index,
                )
            )
        }
    }
}
