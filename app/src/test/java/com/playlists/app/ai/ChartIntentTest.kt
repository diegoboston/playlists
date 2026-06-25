package com.playlists.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartIntentTest {
    @Test
    fun searchQuery_includesArtistNotKey() {
        val intent = ChartIntent(
            action = "find_chart",
            songTitle = "Lean on Me",
            artist = "Bill Withers",
            key = "C",
            playlistName = "Sunday set",
        )
        assertEquals("Lean on Me Bill Withers chords lyrics", intent.searchQuery())
    }

    @Test
    fun fromJson_parsesFields() {
        val json = AiJsonHelper.parseObject(
            """{"action":"find_chart","songTitle":"Amazing Grace","artist":"Traditional","key":"G","playlistName":"Sunday set"}""",
        )!!
        val intent = ChartIntent.fromJson(json, "heard text")!!
        assertEquals("Amazing Grace", intent.songTitle)
        assertEquals("G", intent.key)
    }
}
