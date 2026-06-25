package com.playlists.app.find

import org.junit.Assert.assertEquals
import org.junit.Test

class WebSearchServiceTest {
    @Test
    fun parseResults_extractsLinks() {
        val html = """
            <a class="result__a" href="https://example.com/song">Amazing Grace Chords</a>
            <a class="result__snippet">G C G lyrics</a>
        """.trimIndent()
        val results = WebSearchService.parseResults(html, maxResults = 5)
        assertEquals(1, results.size)
        assertEquals("https://example.com/song", results[0].url)
        assertEquals("Amazing Grace Chords", results[0].title)
    }
}
