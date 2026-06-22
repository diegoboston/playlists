package com.playlists.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index("songId"),
        Index(value = ["playlistId", "position"], unique = true),
    ],
)
data class PlaylistSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

data class PlaylistSongWithDetails(
    val id: Long,
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val title: String,
    val keySignature: String,
    val notes: String,
    val filePath: String,
    val fileType: String,
    val isDeleted: Boolean,
    val isPlaceholder: Boolean,
)
