package com.playlists.app.ai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PastedSongParserTest {
    private fun kinds(values: List<String>) = JSONObject().apply {
        values.forEachIndexed { index, value -> put((index + 1).toString(), value) }
    }

    @Test fun repeatedVersesAreCopiedInFullFromTheOriginal() {
        val verse = listOf("It's been a hard day's night,", "And I've been working like a dog.")
        val text = (listOf("TITLE: Test", "Subscribe now", "[Verse]") + verse + listOf("[Verse]") + verse + verse).joinToString("\n")
        val kinds = listOf("metadata", "clutter", "section", "lyrics", "lyrics", "section", "lyrics", "lyrics", "lyrics", "lyrics")
        val logs = mutableListOf<String>()
        val draft = PastedSongParser.parse(text, JSONObject().put("title", "Test").put("lineKinds", kinds(kinds)), ChartSearchMode.LyricsOnly, logs::add)
        assertEquals(verse + verse + verse, draft.sections.flatMap { it.lines })
        assertTrue(logs.any { it.contains("10/10") })
        assertTrue(logs.any { it.contains("1, 2") })
    }

    @Test fun incompleteOrInvalidClassificationsCannotProducePartialSongs() {
        for (kinds in listOf(listOf("lyrics"), listOf("lyrics", "unknown"))) {
            assertThrows(Exception::class.java) {
                PastedSongParser.parse("First line\nLast line", JSONObject().put("lineKinds", kinds(kinds)), ChartSearchMode.LyricsOnly) {}
            }
        }
    }

    @Test fun chordOnlyLinesAreWrappedAndLyricsRetainEveryOccurrence() {
        val plan = JSONObject().put("lineKinds", kinds(listOf("chords", "lyrics", "lyrics")))
        val draft = PastedSongParser.parse("C Am7 F/C\n[C]Hello\n[C]Hello", plan, ChartSearchMode.ChordsAndLyrics) {}
        assertEquals(listOf("<C> <Am7> <F/C>", "<C>Hello", "<C>Hello"), draft.sections.single().lines)
        val lyrics = PastedSongParser.parse("C Am7 F/C\n[C]Hello\n[C]Hello", plan, ChartSearchMode.LyricsOnly) {}
        assertEquals(listOf("Hello", "Hello"), lyrics.sections.single().lines)
    }
    @Test fun reproducesMissingThreeLabelsAndPreservesAll39LyricLinesWithCompleteIds() {
        val text = javaClass.getResource("/pasted-hard-days-night.txt")!!.readText()
        val original = PastedSongParser.lines(text)
        assertEquals(41, original.size)
        val incomplete = JSONObject().put("lineKinds", kinds(List(38) { "lyrics" }))
        val error = assertThrows(IllegalArgumentException::class.java) {
            PastedSongParser.parse(text, incomplete, ChartSearchMode.LyricsOnly) {}
        }
        assertTrue(error.message!!.contains("received 38 of 41"))
        // IDs, rather than object insertion order, determine the original song order.
        val complete = JSONObject()
        original.indices.reversed().forEach { index ->
            complete.put((index + 1).toString(), if (index < 2) "metadata" else "lyrics")
        }
        val draft = PastedSongParser.parse(text, JSONObject().put("title", "A Hard Day's Night")
            .put("lineKinds", complete), ChartSearchMode.LyricsOnly) {}
        assertEquals(original.drop(2), draft.sections.flatMap { it.lines })
        assertEquals(39, draft.sections.flatMap { it.lines }.size)
    }

    @Test fun sameCountWithWrongLineIdsCannotSilentlyMisalignTheSong() {
        val plan = JSONObject().put("lineKinds", JSONObject().put("1", "lyrics").put("3", "lyrics"))
        assertThrows(IllegalArgumentException::class.java) {
            PastedSongParser.parse("First\nLast", plan, ChartSearchMode.LyricsOnly) {}
        }
    }

    @Test fun unsupportedMetadataLabelCannotDropAnOrdinaryLyricSentence() {
        val text = "TITLE: Test\nTest Artist\nYou know I work all day,\nTo get you money to buy you things.\nWritten by Test Writer"
        val plan = JSONObject().put("title", "Test").put("artist", "Test Artist")
            .put("lineKinds", kinds(listOf("metadata", "metadata", "metadata", "lyrics", "metadata")))
        val draft = PastedSongParser.parse(text, plan, ChartSearchMode.LyricsOnly) {}
        assertEquals(listOf("You know I work all day,", "To get you money to buy you things."), draft.sections.single().lines)
    }

}
