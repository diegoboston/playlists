package com.playlists.app.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PastedSongParserTest {
    @Test fun repeatedVersesAreCopiedInFullFromTheOriginal() {
        val verse = listOf("It's been a hard day's night,", "And I've been working like a dog.")
        val text = (listOf("TITLE: Test", "Subscribe now", "[Verse]") + verse + listOf("[Verse]") + verse + verse).joinToString("\n")
        val kinds = listOf("metadata", "clutter", "section", "lyrics", "lyrics", "section", "lyrics", "lyrics", "lyrics", "lyrics")
        val logs = mutableListOf<String>()
        val draft = PastedSongParser.parse(text, JSONObject().put("title", "Test").put("lineKinds", JSONArray(kinds)), ChartSearchMode.LyricsOnly, logs::add)
        assertEquals(verse + verse + verse, draft.sections.flatMap { it.lines })
        assertTrue(logs.any { it.contains("10/10") })
        assertTrue(logs.any { it.contains("1, 2") })
    }

    @Test fun incompleteOrInvalidClassificationsCannotProducePartialSongs() {
        for (kinds in listOf(listOf("lyrics"), listOf("lyrics", "unknown"))) {
            assertThrows(Exception::class.java) {
                PastedSongParser.parse("First line\nLast line", JSONObject().put("lineKinds", JSONArray(kinds)), ChartSearchMode.LyricsOnly) {}
            }
        }
    }

    @Test fun chordOnlyLinesAreWrappedAndLyricsRetainEveryOccurrence() {
        val plan = JSONObject().put("lineKinds", JSONArray(listOf("chords", "lyrics", "lyrics")))
        val draft = PastedSongParser.parse("C Am7 F/C\n[C]Hello\n[C]Hello", plan, ChartSearchMode.ChordsAndLyrics) {}
        assertEquals(listOf("<C> <Am7> <F/C>", "<C>Hello", "<C>Hello"), draft.sections.single().lines)
        val lyrics = PastedSongParser.parse("C Am7 F/C\n[C]Hello\n[C]Hello", plan, ChartSearchMode.LyricsOnly) {}
        assertEquals(listOf("Hello", "Hello"), lyrics.sections.single().lines)
    }
}
