package com.playlists.app.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTocRendererTest {
    @Test
    fun wrapText_splitsLongLines() {
        val wrapped = PlaylistTocRenderer.wrapText(
            "Amazing Grace how sweet the sound that saved a wretch like me",
            80f,
        ) { text -> text.length * 6f }
        assertTrue(wrapped.size >= 2)
    }

    @Test
    fun wrapText_keepsShortLineSingle() {
        assertEquals(
            listOf("Hello"),
            PlaylistTocRenderer.wrapText("Hello", 400f) { text -> text.length * 6f },
        )
    }
}
