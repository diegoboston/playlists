package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareImporterTest {
    @Test
    fun extractUrl_findsFirstHttpUrl() {
        assertEquals(
            "https://tabs.ultimate-guitar.com/tab/oasis/wonderwall-chords-123",
            ShareImporter.extractUrl("Check this https://tabs.ultimate-guitar.com/tab/oasis/wonderwall-chords-123"),
        )
    }

    @Test
    fun extractUrl_trimsTrailingPunctuation() {
        assertEquals(
            "https://example.com/song",
            ShareImporter.extractUrl("(https://example.com/song)."),
        )
    }

    @Test
    fun extractUrl_returnsNullForPlainText() {
        assertNull(ShareImporter.extractUrl("not a link"))
    }

    @Test
    fun titleHintFromUrl_usesLastPathSegment() {
        assertEquals(
            "wonderwall-chords-123",
            ShareImporter.titleHintFromUrl("https://tabs.ultimate-guitar.com/tab/oasis/wonderwall-chords-123"),
        )
    }
}
