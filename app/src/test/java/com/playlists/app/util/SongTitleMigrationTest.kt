package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTitleMigrationTest {
    @Test
    fun parse_dashSeparatorsBetweenTitleKeyAndInstrument() {
        val result = SongTitleMigration.parse("Amazing Grace - G - electric bass")
        assertEquals("Amazing Grace", result.title)
        assertEquals("G", result.keySignature)
        assertEquals("electric bass", result.notes)
    }

    @Test
    fun parse_dashSeparatorsWithoutSpaces() {
        val result = SongTitleMigration.parse("Amazing Grace-G-voice.pdf")
        assertEquals("Amazing Grace", result.title)
        assertEquals("G", result.keySignature)
        assertEquals("voice", result.notes)
    }

    @Test
    fun parse_dashBetweenTitleAndKeyOnly() {
        val result = SongTitleMigration.parse("Title - Am piano")
        assertEquals("Title", result.title)
        assertEquals("Am", result.keySignature)
        assertEquals("piano", result.notes)
    }

    @Test
    fun parse_replacesUnderscoresAndStripsExtension() {
        val result = SongTitleMigration.parse("Amazing_Grace.pdf")
        assertEquals("Amazing Grace", result.title)
        assertEquals("", result.keySignature)
        assertEquals("", result.notes)
    }

    @Test
    fun parse_extractsKeyBeforeInstrument() {
        val result = SongTitleMigration.parse("Amazing_Grace_C_voice.pdf")
        assertEquals("Amazing Grace", result.title)
        assertEquals("C", result.keySignature)
        assertEquals("voice", result.notes)
    }

    @Test
    fun parse_electricBassWithUnderscores() {
        val result = SongTitleMigration.parse("Song_F_electric_bass")
        assertEquals("Song", result.title)
        assertEquals("F", result.keySignature)
        assertEquals("electric bass", result.notes)
    }

    @Test
    fun parse_electricBassWithSpaces() {
        val result = SongTitleMigration.parse("Amazing Grace G electric bass")
        assertEquals("Amazing Grace", result.title)
        assertEquals("G", result.keySignature)
        assertEquals("electric bass", result.notes)
    }

    @Test
    fun parse_instrumentOnlyNoKey() {
        val result = SongTitleMigration.parse("My Song accordion")
        assertEquals("My Song", result.title)
        assertEquals("", result.keySignature)
        assertEquals("accordion", result.notes)
    }

    @Test
    fun parse_minorKeyAndPiano() {
        val result = SongTitleMigration.parse("Title Am piano")
        assertEquals("Title", result.title)
        assertEquals("Am", result.keySignature)
        assertEquals("piano", result.notes)
    }

    @Test
    fun parse_flatKey() {
        val result = SongTitleMigration.parse("Title Bb voices")
        assertEquals("Title", result.title)
        assertEquals("Bb", result.keySignature)
        assertEquals("voices", result.notes)
    }

    @Test
    fun parse_twoWordKey() {
        val result = SongTitleMigration.parse("Title F sharp voice")
        assertEquals("Title", result.title)
        assertEquals("F#", result.keySignature)
        assertEquals("voice", result.notes)
    }

    @Test
    fun parse_preservesExistingKeyWhenNoneInTitle() {
        val result = SongTitleMigration.parse("Clean Title", existingKey = "D")
        assertEquals("Clean Title", result.title)
        assertEquals("D", result.keySignature)
    }

    @Test
    fun parse_mergesExtractedNotesWithExisting() {
        val result = SongTitleMigration.parse("Song G piano", existingNotes = "slow")
        assertEquals("Song", result.title)
        assertEquals("G", result.keySignature)
        assertEquals("slow, piano", result.notes)
    }

    @Test
    fun parse_doesNotStripLoneKeyToken() {
        val result = SongTitleMigration.parse("G")
        assertEquals("G", result.title)
        assertEquals("", result.keySignature)
    }

    @Test
    fun parse_doesNotTreatArticleAAsKey() {
        assertFalse(SongTitleMigration.isKeyToken("Whole"))
        val result = SongTitleMigration.parse("A Whole New World")
        assertEquals("A Whole New World", result.title)
    }

    @Test
    fun isKeyToken_acceptsCommonChords() {
        assertTrue(SongTitleMigration.isKeyToken("C"))
        assertTrue(SongTitleMigration.isKeyToken("F#"))
        assertTrue(SongTitleMigration.isKeyToken("Bb"))
        assertTrue(SongTitleMigration.isKeyToken("Am"))
        assertTrue(SongTitleMigration.isKeyToken("Gmaj7"))
    }
}
