package com.playlists.app.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTransposerTest {
    @Test
    fun semitonesBetween_fToC_isSeven() {
        assertEquals(7, ChordTransposer.semitonesBetween("F", "C"))
    }

    @Test
    fun transposeLine_fMajorToC() {
        val result = ChordTransposer.transposeLine("<F>  <Bb>  <C>", 7, "C")
        assertTrue(result.contains("<C>"))
        assertTrue(result.contains("<F>"))
        assertTrue(result.contains("<G>"))
    }

    @Test
    fun transposeLine_inKeyOfF_usesBbNotAsharp() {
        val result = ChordTransposer.transposeLine("<F>  <Bb>  <C>", 0, "F")
        assertTrue(result.contains("<Bb>"))
        assertFalse(result.contains("A#"))
    }

    @Test
    fun transposeLine_bracketed_doesNotTransposeLyrics() {
        val result = ChordTransposer.transposeLine("Almeno <Am> tutto", 1, "C")
        assertTrue(result.startsWith("Almeno "))
        assertTrue(result.contains("tutto"))
        assertFalse(result.contains("Blmeno"))
    }

    @Test
    fun transposeLine_unbracketedLine_isUnchanged() {
        val result = ChordTransposer.transposeLine("C  G  Am", 2, "D")
        assertEquals("C  G  Am", result)
    }

    @Test
    fun prefersFlats_fMajor() {
        assertTrue(ChordTransposer.prefersFlats("F"))
        assertFalse(ChordTransposer.prefersFlats("G"))
    }

    @Test
    fun shiftKey_upHalfStep() {
        assertEquals("Gb", ChordTransposer.shiftKey("F", 1))
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
                com.playlists.app.ai.ChartSection("Verse", listOf("<C>  <G>  <Am>")),
            ),
            notes = null,
            sourceUrl = null,
        )
        val up = ChordTransposer.transposeBySemitones(draft, 2)
        assertEquals("D", up.key)
        assertTrue(up.sections.first().lines.first().contains("<D>"))
    }

    @Test
    fun transposeLine_halfDiminishedAndAlterations() {
        assertEquals(
            "<Am7b5>",
            ChordTransposer.transposeLine("<Bbm7b5>", -1, "Am"),
        )
        assertEquals(
            "<G7b9>",
            ChordTransposer.transposeLine("<Ab7b9>", -1, "G"),
        )
        assertEquals(
            "<F#m7(b5)>",
            ChordTransposer.transposeLine("<Gm7(b5)>", -1, "F#m"),
        )
        assertEquals(
            "<Cmaj7#11>",
            ChordTransposer.transposeLine("<Dbmaj7#11>", -1, "C"),
        )
    }

    @Test
    fun transposeLine_slashBassAndSpellingOverride() {
        assertEquals(
            "<B/F#>",
            ChordTransposer.transposeLine("<A/E>", 2, "G", AccidentalSpelling.Sharps),
        )
        assertEquals(
            "<Am7/E>",
            ChordTransposer.transposeLine("<Am7/E>", 0, "Am", AccidentalSpelling.Auto),
        )
        assertEquals(
            "<Bb>",
            ChordTransposer.transposeLine("<A#>", 0, "F", AccidentalSpelling.Flats),
        )
        assertEquals(
            "<A#>",
            ChordTransposer.transposeLine("<Bb>", 0, "G", AccidentalSpelling.Sharps),
        )
    }

    @Test
    fun transposeBySemitones_usesFirstChordWhenKeyMissing() {
        val draft = com.playlists.app.ai.ChartDraft(
            title = "Test",
            artist = null,
            sourceKey = null,
            key = null,
            capo = null,
            columns = 1,
            sections = listOf(
                com.playlists.app.ai.ChartSection("Verse", listOf("<Am>  <Dm>")),
            ),
            notes = null,
            sourceUrl = null,
        )
        val up = ChordTransposer.transposeBySemitones(draft, 2)
        assertEquals("Bm", up.key)
    }
}
