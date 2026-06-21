package com.playlists.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): Song?

    @Insert
    suspend fun insert(song: Song): Long

    @Query("UPDATE songs SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun markDeleted(id: Long, deletedAt: Long)

    @Query(
        """
        SELECT * FROM songs
        WHERE deletedAt IS NULL
          AND (title LIKE '%' || :query || '%'
           OR keySignature LIKE '%' || :query || '%'
           OR notes LIKE '%' || :query || '%')
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    suspend fun search(query: String): List<Song>

    @Query("UPDATE songs SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    @Query("UPDATE songs SET sortOrder = sortOrder + 1 WHERE deletedAt IS NULL")
    suspend fun incrementAllSortOrders()

    @Transaction
    suspend fun insertAtTop(song: Song): Long {
        incrementAllSortOrders()
        return insert(song.copy(sortOrder = 0))
    }

    @Transaction
    suspend fun replaceOrder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id ->
            updateSortOrder(id, index)
        }
    }
}
