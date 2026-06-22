package com.playlists.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistSongDao {
    @Query(
        """
        SELECT ps.id, ps.playlistId, ps.songId, ps.position,
               s.title, s.keySignature, s.notes, s.filePath, s.fileType,
               (s.deletedAt IS NOT NULL) AS isDeleted
        FROM playlist_songs ps
        INNER JOIN songs s ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
        """
    )
    fun observeForPlaylist(playlistId: Long): Flow<List<PlaylistSongWithDetails>>

    @Query(
        """
        SELECT ps.id, ps.playlistId, ps.songId, ps.position,
               s.title, s.keySignature, s.notes, s.filePath, s.fileType,
               (s.deletedAt IS NOT NULL) AS isDeleted
        FROM playlist_songs ps
        INNER JOIN songs s ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
        """
    )
    suspend fun getForPlaylist(playlistId: Long): List<PlaylistSongWithDetails>

    @Insert
    suspend fun insert(entry: PlaylistSong): Long

    @Query("DELETE FROM playlist_songs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteAllForPlaylist(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("UPDATE playlist_songs SET position = :position WHERE id = :entryId AND playlistId = :playlistId")
    suspend fun updatePosition(playlistId: Long, entryId: Long, position: Int)

    @Transaction
    suspend fun replaceOrder(playlistId: Long, entryIdsInOrder: List<Long>) {
        entryIdsInOrder.forEachIndexed { index, entryId ->
            updatePosition(playlistId, entryId, -(index + 1))
        }
        entryIdsInOrder.forEachIndexed { index, entryId ->
            updatePosition(playlistId, entryId, index)
        }
    }
}
