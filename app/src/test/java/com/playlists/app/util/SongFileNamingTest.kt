package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SongFileNamingTest {
    @Test
    fun format_usesUnderscoresInTitleAndDashesBeforeKeyAndId() {
        assertEquals(
            "Amazing_Grace-G-42.pdf",
            SongFileNaming.format("Amazing Grace", "G", 42, "pdf"),
        )
    }

    @Test
    fun format_emptyKeyStillIncludesSeparator() {
        assertEquals(
            "Amazing_Grace--42.pdf",
            SongFileNaming.format("Amazing Grace", "", 42, "pdf"),
        )
    }

    @Test
    fun format_sanitizesInvalidCharacters() {
        assertEquals(
            "What_a_Friend-G-7.png",
            SongFileNaming.format("What a Friend?", "G", 7, "png"),
        )
    }

    @Test
    fun format_blankTitleFallsBackToUntitled() {
        assertEquals(
            "Untitled-C-1.jpg",
            SongFileNaming.format("  ", "C", 1, "jpg"),
        )
    }

    @Test
    fun matches_returnsTrueForCanonicalName() {
        val file = File("/tmp/Amazing_Grace-G-42.pdf")
        assertTrue(SongFileNaming.matches("Amazing Grace", "G", 42, file))
    }

    @Test
    fun matches_returnsFalseForUuidName() {
        val file = File("/tmp/8f3c2a1b-4d5e-6f70-8a9b-0c1d2e3f4a5b.pdf")
        assertFalse(SongFileNaming.matches("Amazing Grace", "G", 42, file))
    }
}
