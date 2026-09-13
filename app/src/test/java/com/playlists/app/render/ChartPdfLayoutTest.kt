package com.playlists.app.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartPdfLayoutTest {
    private fun metrics(bodyLine: Float = 20f, titleLine: Float = 28f) =
        ChartPdfLayout.metricsFor(
            textSize = ChartPdfLayout.MAX_BODY_SIZE,
            hasNotes = false,
            titleLineHeight = titleLine,
            labelLineHeight = bodyLine,
            bodyLineHeight = bodyLine,
        )

    private fun lineItems(count: Int): List<ChartPdfLayout.Item> =
        List(count) { ChartPdfLayout.Item.Line("line $it") }

    @Test
    fun fitsOnePage_shortChart() {
        val items = lineItems(5)
        assertTrue(ChartPdfLayout.fitsOnePage(items, metrics()))
    }

    @Test
    fun paginate_splitsWhenContentExceedsPage() {
        val items = lineItems(60)
        val metrics = metrics(bodyLine = 20f)
        assertFalse(ChartPdfLayout.fitsOnePage(items, metrics))

        val pages = ChartPdfLayout.paginate(items, metrics)
        assertTrue(pages.size >= 2)
        assertEquals(items.size, pages.sumOf { it.size })
    }

    @Test
    fun paginate_threePagesForVeryLongChart() {
        val items = lineItems(120)
        val metrics = metrics(bodyLine = 20f)
        val pages = ChartPdfLayout.paginate(items, metrics)
        assertTrue(pages.size >= 3)
    }

    @Test
    fun chooseTextSize_prefersSinglePageShrinkBeforePaginating() {
        val items = lineItems(48)
        val chosen = ChartPdfLayout.chooseTextSize(
            items = items,
            hasNotes = false,
            lineHeight = { size, isTitle ->
                if (isTitle) size + 8f else size
            },
        )
        val metrics = ChartPdfLayout.metricsFor(
            textSize = chosen,
            hasNotes = false,
            titleLineHeight = chosen + 8f,
            labelLineHeight = chosen,
            bodyLineHeight = chosen,
        )
        assertTrue(ChartPdfLayout.fitsOnePage(items, metrics))
        assertTrue(chosen < ChartPdfLayout.MAX_BODY_SIZE)
    }

    @Test
    fun chooseTextSize_usesDefaultBodyWhenPaginationRequired() {
        val items = lineItems(80)
        val chosen = ChartPdfLayout.chooseTextSize(
            items = items,
            hasNotes = false,
            lineHeight = { size, _ -> size },
        )
        assertEquals(ChartPdfLayout.MAX_BODY_SIZE, chosen)
        val metrics = metrics(bodyLine = chosen)
        assertFalse(ChartPdfLayout.fitsOnePage(items, metrics))
    }

    @Test
    fun chooseTextSize_prefersSinglePageShrinkDownToMin() {
        val items = lineItems(50)
        val chosen = ChartPdfLayout.chooseTextSize(
            items = items,
            hasNotes = false,
            lineHeight = { size, isTitle ->
                if (isTitle) size + 8f else size
            },
        )
        val metrics = ChartPdfLayout.metricsFor(
            textSize = chosen,
            hasNotes = false,
            titleLineHeight = chosen + 8f,
            labelLineHeight = chosen,
            bodyLineHeight = chosen,
        )
        assertTrue(ChartPdfLayout.fitsOnePage(items, metrics))
        assertTrue(chosen < ChartPdfLayout.MAX_BODY_SIZE)
        assertTrue(chosen >= ChartPdfLayout.MIN_TEXT_SIZE)
    }

    @Test
    fun chooseTextSize_acceptsPreferredUpToMaxFont() {
        val items = lineItems(5)
        val chosen = ChartPdfLayout.chooseTextSize(
            items = items,
            hasNotes = false,
            lineHeight = { size, _ -> size },
            preferredSize = 20f,
        )
        assertEquals(ChartPdfLayout.MAX_FONT_SIZE, chosen)
    }

    @Test
    fun chooseTextSize_clampsPreferredAboveMaxFont() {
        val items = lineItems(5)
        val chosen = ChartPdfLayout.chooseTextSize(
            items = items,
            hasNotes = false,
            lineHeight = { size, _ -> size },
            preferredSize = 24f,
        )
        assertEquals(ChartPdfLayout.MAX_FONT_SIZE, chosen)
    }

    @Test
    fun layoutPages_splitsLongChart() {
        val items = lineItems(80)
        val metrics = metrics(bodyLine = ChartPdfLayout.MAX_BODY_SIZE)
        val pages = ChartPdfLayout.layoutPages(items, metrics)
        assertTrue(pages.size >= 2)
    }

    @Test
    fun flatten_includesSectionLabelsAndGaps() {
        val blocks = listOf(
            ChartPdfLayout.Block("Verse", listOf("<G> Hello")),
            ChartPdfLayout.Block("", listOf("<C> World")),
        )
        val items = ChartPdfLayout.flatten(blocks)
        assertEquals(
            listOf(
                ChartPdfLayout.Item.SectionLabel("Verse"),
                ChartPdfLayout.Item.Line("<G> Hello"),
                ChartPdfLayout.Item.SectionGap,
                ChartPdfLayout.Item.Line("<C> World"),
                ChartPdfLayout.Item.SectionGap,
            ),
            items,
        )
    }
}
