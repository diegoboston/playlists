package com.playlists.app.ui

import com.playlists.app.data.PlaylistSongWithDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFramesTest {
    @Test
    fun buildTocEntries_includesAllPlaylistEntries() {
        val songs = listOf(
            entry(id = 1, title = "Song A", key = "G"),
            entry(id = 2, title = "Song B", key = "Am"),
        )
        val toc = buildTocEntries(songs)
        assertEquals(2, toc.size)
        assertEquals("Song A", toc[0].title)
        assertEquals("G", toc[0].keySignature)
        assertEquals("Song B", toc[1].title)
    }

    @Test
    fun buildTocEntries_keepsPlaceholderTitles() {
        val songs = listOf(entry(id = 1, title = "Intro ${SongDisplay.PLACEHOLDER_MARKER}", key = ""))
        assertEquals("Intro ${SongDisplay.PLACEHOLDER_MARKER}", buildTocEntries(songs).single().title)
    }

    @Test
    fun sanitizeExportFilename_replacesUnsafeCharacters() {
        assertEquals("Sunday_Set", sanitizeExportFilename("Sunday Set"))
        assertEquals("playlist", sanitizeExportFilename("***"))
    }

    private fun entry(
        id: Long,
        title: String,
        key: String,
        path: String = "Music/StageManager/songs/$id.pdf",
    ): PlaylistSongWithDetails = PlaylistSongWithDetails(
        id = id,
        playlistId = 1L,
        songId = id,
        position = id.toInt(),
        title = title,
        keySignature = key,
        notes = "",
        filePath = path,
        fileType = "PDF",
    )
}
