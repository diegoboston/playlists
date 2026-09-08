package com.playlists.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartTextParserTest {
    @Test
    fun parse_readsHeadersAndSections() {
        val raw = """
            TITLE: Lean on Me
            ARTIST: Bill Withers
            KEY: C
            CAPO:

            [Verse 1]
            <C>  <F>  <C>
            Sometimes in our lives

            [Chorus]
            Lean on me
        """.trimIndent()
        val draft = ChartTextParser.parse(raw, sourceUrl = "https://example.com")!!
        assertEquals("Lean on Me", draft.title)
        assertEquals("Bill Withers", draft.artist)
        assertEquals("C", draft.key)
        assertNull(draft.capo)
        assertEquals(2, draft.sections.size)
        assertEquals("Verse 1", draft.sections[0].label)
        assertEquals("<C>  <F>  <C>", draft.sections[0].lines[0])
        assertEquals("https://example.com", draft.sourceUrl)
    }

    @Test
    fun parse_keepsCompleteLinesWhenTruncated() {
        val raw = """
            TITLE: X Colpa Di Chi?
            ARTIST: Zucchero

            [Verse 1]
            Funky gallo, come sono bello stamattina
            Non c'e piu la mia morosa

            [Chorus
        """.trimIndent()
        val draft = ChartTextParser.parse(raw, fallbackTitle = "X Colpa di chi")!!
        assertEquals("X Colpa Di Chi?", draft.title)
        assertEquals(1, draft.sections.size)
        assertEquals("Funky gallo, come sono bello stamattina", draft.sections[0].lines[0])
    }

    @Test
    fun parse_usesFallbackTitle() {
        val raw = """
            [Verse 1]
            Nel blu dipinto di blu
        """.trimIndent()
        val draft = ChartTextParser.parse(raw, fallbackTitle = "Volare")!!
        assertEquals("Volare", draft.title)
        assertEquals("Nel blu dipinto di blu", draft.sections[0].lines[0])
    }

    @Test
    fun parse_blank_returnsNull() {
        assertNull(ChartTextParser.parse("TITLE: Only"))
        assertNull(ChartTextParser.parse(""))
    }
}
