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
}
