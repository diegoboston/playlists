package com.playlists.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): Playlist?

    @Insert
    suspend fun insert(playlist: Playlist): Long

    @Update
    suspend fun update(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE playlists SET sortOrder = sortOrder + 1")
    suspend fun incrementAllSortOrders()

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM playlists")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE playlists SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    @Transaction
    suspend fun insertAtTop(playlist: Playlist): Long {
        incrementAllSortOrders()
        return insert(playlist.copy(sortOrder = 0))
    }

    @Transaction
    suspend fun replaceOrder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id ->
            updateSortOrder(id, index)
        }
    }
}
