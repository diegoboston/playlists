package com.playlists.app.data

import kotlinx.coroutines.flow.Flow

class SongRepository(private val songDao: SongDao) {
    fun observeAll(): Flow<List<Song>> = songDao.observeAll()

    suspend fun getAll(): List<Song> = songDao.getAll()

    suspend fun getById(id: Long): Song? = songDao.getById(id)

    suspend fun insert(song: Song): Long = songDao.insert(song)

    suspend fun delete(id: Long) = songDao.deleteById(id)

    suspend fun search(query: String): List<Song> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return songDao.getAll()
        return songDao.search(trimmed)
    }
}

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val playlistSongDao: PlaylistSongDao,
) {
    fun observeAll(): Flow<List<Playlist>> = playlistDao.observeAll()

    suspend fun getById(id: Long): Playlist? = playlistDao.getById(id)

    suspend fun create(name: String): Long =
        playlistDao.insert(Playlist(name = name))

    suspend fun rename(id: Long, name: String) {
        val playlist = playlistDao.getById(id) ?: return
        playlistDao.update(playlist.copy(name = name))
    }

    suspend fun delete(id: Long) = playlistDao.deleteById(id)

    suspend fun duplicate(id: Long): Long? {
        val source = playlistDao.getById(id) ?: return null
        val entries = playlistSongDao.getForPlaylist(id)
        val newId = playlistDao.insert(
            Playlist(name = "${source.name} (copy)")
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

    suspend fun reorder(playlistId: Long, songIdsInOrder: List<Long>) {
        playlistSongDao.replaceOrder(playlistId, songIdsInOrder)
    }

    suspend fun setSongs(playlistId: Long, songIds: List<Long>) {
        playlistSongDao.replaceOrder(playlistId, songIds)
    }
}
