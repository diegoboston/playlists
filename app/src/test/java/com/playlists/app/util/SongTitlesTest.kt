package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SongTitlesTest {
    @Test
    fun fromFilename_replacesUnderscoresAndStripsExtension() {
        assertEquals("Amazing Grace", SongTitles.fromFilename("Amazing_Grace.pdf"))
        assertEquals("my song", SongTitles.fromFilename("my_song.png"))
        assertEquals("no ext", SongTitles.fromFilename("no_ext"))
    }

    @Test
    fun parseFilename_extractsTitleKeyAndNotes() {
        val parsed = SongTitles.parseFilename("Amazing_Grace_C_voice.pdf")
        assertEquals("Amazing Grace", parsed.title)
        assertEquals("C", parsed.keySignature)
        assertEquals("voice", parsed.notes)
    }
}
