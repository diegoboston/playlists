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
        assertEquals("G C G lyrics", results[0].snippet)
    }

    @Test
    fun parseResults_decodesSnippetEntities() {
        val html = """
            <a class="result__a" href="https://example.com/song">Volare</a>
            <a class="result__snippet">Nel blu&#x27; dipinto &amp; blu</a>
        """.trimIndent()
        val results = WebSearchService.parseResults(html, maxResults = 5)
        assertEquals("Nel blu' dipinto & blu", results[0].snippet)
    }

    @Test
    fun lyricRegionHtml_prefersLyricsComPre() {
        val html = """
            <nav>${"chrome ".repeat(40)}</nav>
            <pre id="lyric-body-text" class="lyric-body">Funky gallo, come sono <a href="/x">bello</a> stamattina
Non c'e piu la mia morosa e sono piu leggero di una piuma
Oh e intanto Zio Rufus sta</pre>
            <footer>ads</footer>
        """.trimIndent()
        val region = PageFetcher.lyricRegionHtml(html)!!
        assertEquals(false, region.contains("chrome"))
        val text = PageFetcher.htmlToText(region)
        assertEquals(true, text.contains("Funky gallo, come sono bello stamattina"))
    }

    @Test
    fun withSearchSnippet_prependsWhenPresent() {
        assertEquals(
            "Search result snippet:\nFunky gallo\n\nPage text:\nbody",
            PageFetcher.withSearchSnippet("Funky gallo", "body"),
        )
        assertEquals("body", PageFetcher.withSearchSnippet("  ", "body"))
    }
}
