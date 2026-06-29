package com.playlists.app.util

import com.playlists.app.ui.SongDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFileNamesTest {
    @Test
    fun mediaFileName_usesSanitizedTitleAndSongId() {
        assertEquals(
            "Amazing_Grace-42.pdf",
            SongFileNames.mediaFileName("Amazing Grace", 42, "pdf"),
        )
    }

    @Test
    fun mediaFileName_stripsPlaceholderMarker() {
        assertEquals(
            "My_Song-7.png",
            SongFileNames.mediaFileName("My Song${SongDisplay.PLACEHOLDER_MARKER}", 7, "png"),
        )
    }

    @Test
    fun chartFileName_matchesMediaBase() {
        assertEquals(
            "Amazing_Grace-42.chart.json",
            SongFileNames.chartFileName("Amazing Grace", 42),
        )
    }

    @Test
    fun isUuidFileName_detectsUuidFiles() {
        assertTrue(
            SongFileNames.isUuidFileName("a1b2c3d4-e5f6-7890-abcd-ef1234567890.pdf"),
        )
        assertFalse(SongFileNames.isUuidFileName("Amazing_Grace-42.pdf"))
    }

    @Test
    fun isCanonicalMediaFileName_acceptsTitleIdPattern() {
        assertTrue(SongFileNames.isCanonicalMediaFileName("Amazing_Grace-42.pdf"))
        assertFalse(SongFileNames.isCanonicalMediaFileName("a1b2c3d4-e5f6-7890-abcd-ef1234567890.pdf"))
        assertFalse(SongFileNames.isCanonicalMediaFileName("placeholder-42.png"))
    }

    @Test
    fun matchesSong_comparesExpectedName() {
        assertTrue(SongFileNames.matchesSong("Amazing_Grace-42.pdf", "Amazing Grace", 42))
        assertFalse(SongFileNames.matchesSong("Amazing_Grace-43.pdf", "Amazing Grace", 42))
    }
}
