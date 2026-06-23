package com.playlists.app.ui

import com.playlists.app.data.Song

data class SongDeletePrompt(
    val song: Song,
    val playlistNames: List<String>,
    val deleteFileOnConfirm: Boolean,
)
