package com.playlists.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTest {
    @Test
    fun isAiLyrics_matchesNotesPrefix() {
        assertTrue(song(notes = "AI lyrics").isAiLyrics())
        assertTrue(song(notes = "AI lyrics · https://example.com").isAiLyrics())
        assertTrue(song(notes = "ai lyrics · https://example.com").isAiLyrics())
        assertFalse(song(notes = "AI chart · https://example.com").isAiLyrics())
        assertFalse(song(notes = "intro notes").isAiLyrics())
        assertFalse(song(notes = "").isAiLyrics())
    }

    private fun song(notes: String) = Song(
        title = "Song",
        keySignature = "C",
        notes = notes,
        filePath = "songs/song.pdf",
        fileType = FileType.PDF.name,
    )
}
