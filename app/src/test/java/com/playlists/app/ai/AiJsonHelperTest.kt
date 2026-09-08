package com.playlists.app.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiJsonHelperTest {
    @Test
    fun playlistsContext_includesIdsAndCurrent() {
        val json = AiJsonHelper.playlistsContext(
            listOf(1L to "Sunday set", 2L to "Jazz"),
            currentPlaylistId = 2,
        )
        val obj = JSONObject(json)
        assertEquals(2, obj.getInt("currentPlaylistId"))
        val playlists = obj.getJSONArray("playlists")
        assertEquals(2, playlists.length())
        assertEquals("Sunday set", playlists.getJSONObject(0).getString("name"))
        assertEquals(1, playlists.getJSONObject(0).getLong("id"))
        assertEquals("Jazz", playlists.getJSONObject(1).getString("name"))
    }

    @Test
    fun playlistsContext_nullCurrentIsJsonNull() {
        val json = AiJsonHelper.playlistsContext(emptyList(), currentPlaylistId = null)
        val obj = JSONObject(json)
        assertTrue(obj.isNull("currentPlaylistId"))
        assertEquals(0, obj.getJSONArray("playlists").length())
    }

    @Test
    fun salvageChartJson_recoversCompleteSectionsFromTruncatedResponse() {
        val truncated = """
            {
              "title": "X Colpa Di Chi?",
              "artist": "Zucchero",
              "sourceKey": null,
              "key": null,
              "capo": null,
              "columns": 1,
              "sections": [
                {
                  "label": "Verse 1",
                  "lines": [
                    "Funky gallo, come sono bello stamattina",
                    "Non c'e piu la mia morosa e sono piu leggero di una piuma"
                  ]
                },
                {
                  "label": "Chorus
        """.trimIndent()
        val json = AiJsonHelper.salvageChartJson(truncated)!!
        assertEquals("X Colpa Di Chi?", json.getString("title"))
        assertEquals("Zucchero", json.getString("artist"))
        val sections = json.getJSONArray("sections")
        assertEquals(1, sections.length())
        assertEquals("Verse 1", sections.getJSONObject(0).getString("label"))
        assertEquals(
            "Funky gallo, come sono bello stamattina",
            sections.getJSONObject(0).getJSONArray("lines").getString(0),
        )
        val draft = ChartDraft.fromJson(json)!!
        assertEquals("X Colpa Di Chi?", draft.title)
        assertEquals(1, draft.sections.size)
    }

    @Test
    fun salvageChartJson_nullWhenNoCompleteSection() {
        val raw = """{"title":"Volare","sections":[{"label":"Verse 1","lines":["""
        assertEquals(null, AiJsonHelper.salvageChartJson(raw))
    }
}
