package com.playlists.app.data

import com.playlists.app.ui.SongDisplay
import com.playlists.app.util.FileStorage
import com.playlists.app.util.PlaceholderImageGenerator
import com.playlists.app.util.SongStoragePaths
import java.io.File
import kotlinx.coroutines.flow.Flow

class SongRepository(private val songDao: SongDao) {
    fun observeAll(): Flow<List<Song>> = songDao.observeAll()

    suspend fun getAll(): List<Song> = songDao.getAll()

    suspend fun getById(id: Long): Song? = songDao.getById(id)

    suspend fun insert(song: Song): Long = songDao.insertAtTop(song)

    suspend fun update(song: Song) {
        songDao.update(song)
    }

    suspend fun delete(id: Long) {
        val song = songDao.getById(id) ?: return
        val storedPath = song.filePath
        songDao.deleteById(id)
        val file = SongStoragePaths.resolve(storedPath)
        if (!file.isFile) return
        val stillReferenced = songDao.getAll().any { it.filePath == storedPath }
        if (!stillReferenced) {
            file.delete()
        }
    }

    suspend fun sortAlpha(reverse: Boolean = false) {
        val ids = songDao.getAll()
            .sortedWith(
                if (reverse) {
                    compareByDescending<Song> { it.title.lowercase() }.thenByDescending { it.id }
                } else {
                    compareBy<Song> { it.title.lowercase() }.thenBy { it.id }
                },
            )
            .map { it.id }
        songDao.replaceOrder(ids)
    }

    suspend fun sortByRecentlyAdded(reverse: Boolean = false) {
        val ids = songDao.getAll()
            .sortedWith(
                if (reverse) {
                    compareBy<Song> { it.createdAt }.thenBy { it.id }
                } else {
                    compareByDescending<Song> { it.createdAt }.thenByDescending { it.id }
                },
            )
            .map { it.id }
        songDao.replaceOrder(ids)
    }

    suspend fun sortByRecentlyViewed(reverse: Boolean = false) {
        val ids = songDao.getAll()
            .sortedWith(
                if (reverse) {
                    compareBy<Song> { it.lastViewedAt == null }
                        .thenBy { it.lastViewedAt }
                        .thenBy { it.id }
                } else {
                    compareBy<Song> { it.lastViewedAt == null }
                        .thenByDescending { it.lastViewedAt }
                        .thenByDescending { it.id }
                },
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
        title: String,
        keySignature: String = "",
    ): Long {
        val trimmedTitle = title.trim().ifBlank { "Untitled" }
        val bytes = PlaceholderImageGenerator.render(trimmedTitle)
        val stored = FileStorage.storeBytes(bytes, "png")
        val id = insert(
            Song(
                title = trimmedTitle + SongDisplay.PLACEHOLDER_MARKER,
                keySignature = keySignature.trim(),
                notes = "placeholder",
                filePath = SongStoragePaths.toStoredPath(stored),
                fileType = FileType.IMAGE.name,
            ),
        )
        renamePlaceholderFile(id)
        return id
    }

    private suspend fun renamePlaceholderFile(songId: Long) {
        val song = songDao.getById(songId) ?: return
        val current = SongStoragePaths.resolve(song.filePath)
        if (!current.isFile) return
        val target = File(current.parentFile, "placeholder-$songId.png")
        if (target.exists() && target.absolutePath != current.absolutePath) return
        if (!current.renameTo(target)) return
        songDao.update(song.copy(filePath = SongStoragePaths.toStoredPath(target)))
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
