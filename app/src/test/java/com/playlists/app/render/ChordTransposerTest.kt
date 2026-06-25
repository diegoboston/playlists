package com.playlists.app.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTransposerTest {
    @Test
    fun semitonesBetween_fToC_isSeven() {
        assertEquals(7, ChordTransposer.semitonesBetween("F", "C"))
    }

    @Test
    fun transposeLine_fMajorToC() {
        val result = ChordTransposer.transposeLine("F  Bb  C", 7)
        assertTrue(result.contains("C"))
        assertTrue(result.contains("F"))
        assertTrue(result.contains("G"))
    }

    @Test
    fun shiftKey_upHalfStep() {
        assertEquals("F#", ChordTransposer.shiftKey("F", 1))
        assertEquals("G", ChordTransposer.shiftKey("F", 2))
    }

    @Test
    fun shiftKey_minorKey() {
        assertEquals("Bm", ChordTransposer.shiftKey("Am", 2))
    }

    @Test
    fun transposeBySemitones_updatesKeyAndChords() {
        val draft = com.playlists.app.ai.ChartDraft(
            title = "Test",
            artist = null,
            sourceKey = "C",
            key = "C",
            capo = null,
            columns = 1,
            sections = listOf(
                com.playlists.app.ai.ChartSection("Verse", listOf("C  G  Am")),
            ),
            notes = null,
            sourceUrl = null,
        )
        val up = ChordTransposer.transposeBySemitones(draft, 2)
        assertEquals("D", up.key)
        assertTrue(up.sections.first().lines.first().contains("D"))
    }
}
