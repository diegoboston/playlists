package com.playlists.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY createdAt DESC")
    suspend fun getAll(): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): Song?

    @Insert
    suspend fun insert(song: Song): Long

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT * FROM songs
        WHERE title LIKE '%' || :query || '%'
           OR keySignature LIKE '%' || :query || '%'
           OR notes LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    suspend fun search(query: String): List<Song>
}
