package com.playlists.app.util

import com.playlists.app.ai.ChartDraft
import com.playlists.app.ai.ChartSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChartDraftStoreTest {
    @Test
    fun chartFileName_derivesFromPdf() {
        assertEquals("abc-123.chart.json", ChartDraftStore.chartFileName("abc-123.pdf"))
    }

    @Test
    fun chartStoredPath_isRelative() {
        assertEquals(
            "Music/StageManager/songs/foo.chart.json",
            ChartDraftStore.chartStoredPath("Music/StageManager/songs/foo.pdf"),
        )
    }

    @Test
    fun draftJson_roundTrip() {
        val draft = ChartDraft(
            title = "Test Song",
            artist = "Artist",
            sourceKey = "F",
            key = "F",
            capo = null,
            columns = 1,
            sections = listOf(ChartSection("Verse", listOf("F  Bb  C"))),
            notes = null,
            sourceUrl = "https://example.com",
        )
        val loaded = ChartDraft.fromJson(draft.toJson())
        assertNotNull(loaded)
        assertEquals("Test Song", loaded!!.title)
        assertEquals("F", loaded.sourceKey)
        assertEquals(1, loaded.sections.size)
        assertEquals("F  Bb  C", loaded.sections.first().lines.first())
    }
}
