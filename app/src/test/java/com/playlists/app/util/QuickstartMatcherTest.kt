package com.playlists.app.util

import com.playlists.app.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuickstartMatcherTest {
    private val archive = listOf(
        Song(id = 1, title = "Amazing Grace", keySignature = "G", notes = "", filePath = "", fileType = "PDF", mimeType = ""),
        Song(id = 2, title = "Blue Moon", keySignature = "C", notes = "", filePath = "", fileType = "PDF", mimeType = ""),
    )

    @Test
    fun exactTitleMatch() {
        val results = QuickstartMatcher.matchLines(listOf("Amazing Grace"), archive)
        assertEquals("Amazing Grace", results.single().song?.title)
    }

    @Test
    fun fuzzyMatch() {
        val results = QuickstartMatcher.matchLines(listOf("blue moon"), archive)
        assertNotNull(results.single().song)
    }

    @Test
    fun noMatchForUnknown() {
        val results = QuickstartMatcher.matchLines(listOf("xyz unknown song"), archive)
        assertNull(results.single().song)
    }
}
