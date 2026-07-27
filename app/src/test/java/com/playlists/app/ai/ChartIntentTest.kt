package com.playlists.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartIntentTest {
    @Test
    fun searchQuery_chordsAndLyrics_includesArtistNotKey() {
        val intent = ChartIntent(
            action = "find_chart",
            songTitle = "Lean on Me",
            artist = "Bill Withers",
            key = "C",
            playlistName = "Sunday set",
            searchMode = ChartSearchMode.ChordsAndLyrics,
        )
        assertEquals("Lean on Me Bill Withers chords lyrics", intent.searchQuery())
    }

    @Test
    fun searchQuery_lyricsOnly_usesLyricsSuffix() {
        val intent = ChartIntent(
            action = "find_chart",
            songTitle = "Amazing Grace",
            artist = "John Newton",
            key = null,
            playlistName = null,
            searchMode = ChartSearchMode.LyricsOnly,
        )
        assertEquals("Amazing Grace John Newton lyrics", intent.searchQuery())
    }

    @Test
    fun fromTypedQuery_parsesTitleByArtist() {
        val intent = ChartIntent.fromTypedQuery(
            "  Lean on Me  by  Bill Withers  ",
            ChartSearchMode.ChordsAndLyrics,
            playlistName = "Sunday set",
        )!!
        assertEquals("Lean on Me", intent.songTitle)
        assertEquals("Bill Withers", intent.artist)
        assertEquals("Lean on Me by Bill Withers", intent.transcript)
        assertEquals("Lean on Me Bill Withers chords lyrics", intent.searchQuery())
    }

    @Test
    fun fromTypedQuery_titleOnly_lyricsMode() {
        val intent = ChartIntent.fromTypedQuery(
            "Wagon Wheel",
            ChartSearchMode.LyricsOnly,
        )!!
        assertEquals("Wagon Wheel", intent.songTitle)
        assertNull(intent.artist)
        assertEquals("Wagon Wheel lyrics", intent.searchQuery())
    }

    @Test
    fun fromTypedQuery_blank_returnsNull() {
        assertNull(ChartIntent.fromTypedQuery("   ", ChartSearchMode.ChordsAndLyrics))
    }

    @Test
    fun fromJson_parsesFields() {
        val json = AiJsonHelper.parseObject(
            """{"action":"find_chart","songTitle":"Amazing Grace","artist":"Traditional","key":"G","playlistName":"Sunday set"}""",
        )!!
        val intent = ChartIntent.fromJson(json, "heard text", ChartSearchMode.LyricsOnly)!!
        assertEquals("Amazing Grace", intent.songTitle)
        assertEquals("G", intent.key)
        assertEquals(ChartSearchMode.LyricsOnly, intent.searchMode)
        assertEquals("Amazing Grace Traditional lyrics", intent.searchQuery())
        assertEquals("Amazing Grace by Traditional", intent.editableQuery())
    }
}
