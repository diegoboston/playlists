package com.playlists.app.render

/**
 * Pure layout helpers for [ChartPdfRenderer]: flatten sections into drawable items
 * and pack them onto letter-sized pages.
 */
internal object ChartPdfLayout {
    const val MIN_TEXT_SIZE = 9f
    const val MAX_TITLE_SIZE = 28f
    /** Auto-fit start and fallback when a chart must paginate. */
    const val MAX_BODY_SIZE = 14f
    /** User-adjustable ceiling in AI chart / Reformat previews. */
    const val MAX_FONT_SIZE = 20f

    data class Block(val label: String, val lines: List<String>)

    sealed interface Item {
        data class SectionLabel(val text: String) : Item
        data class Line(val text: String) : Item
        data object SectionGap : Item
    }

    data class TextMetrics(
        val headerHeight: Float,
        val headerGap: Float,
        val labelLineHeight: Float,
        val bodyLineHeight: Float,
        val sectionGap: Float,
        val continuationHeaderHeight: Float,
    )

    fun flatten(blocks: List<Block>): List<Item> = buildList {
        blocks.forEach { block ->
            if (block.label.isNotEmpty()) {
                add(Item.SectionLabel(block.label))
            }
            block.lines.forEach { line ->
                add(Item.Line(line))
            }
            add(Item.SectionGap)
        }
    }

    fun metricsFor(
        textSize: Float,
        hasNotes: Boolean,
        titleLineHeight: Float,
        labelLineHeight: Float,
        bodyLineHeight: Float,
    ): TextMetrics {
        val headerHeight = titleLineHeight * 2f + if (hasNotes) 12f else 0f
        return TextMetrics(
            headerHeight = headerHeight,
            headerGap = bodyLineHeight * 0.5f,
            labelLineHeight = labelLineHeight,
            bodyLineHeight = bodyLineHeight,
            sectionGap = bodyLineHeight * 0.35f,
            continuationHeaderHeight = titleLineHeight * 0.85f,
        )
    }

    fun itemHeight(item: Item, metrics: TextMetrics): Float = when (item) {
        is Item.SectionLabel -> metrics.labelLineHeight
        is Item.Line -> metrics.bodyLineHeight
        Item.SectionGap -> metrics.sectionGap
    }

    fun contentHeight(items: List<Item>, metrics: TextMetrics): Float =
        items.sumOf { itemHeight(it, metrics).toDouble() }.toFloat()

    fun fitsOnePage(
        items: List<Item>,
        metrics: TextMetrics,
        pageHeight: Float = PdfPageSpec.HEIGHT.toFloat(),
        margin: Float = PdfPageSpec.MARGIN,
    ): Boolean {
        val capacity = pageHeight - margin * 2f
        return metrics.headerHeight + metrics.headerGap + contentHeight(items, metrics) <= capacity
    }

    fun paginate(
        items: List<Item>,
        metrics: TextMetrics,
        pageHeight: Float = PdfPageSpec.HEIGHT.toFloat(),
        margin: Float = PdfPageSpec.MARGIN,
    ): List<List<Item>> {
        val pageCapacity = pageHeight - margin * 2f
        val firstPageCapacity = pageCapacity - metrics.headerHeight - metrics.headerGap
        val continuationCapacity = pageCapacity - metrics.continuationHeaderHeight

        val pages = mutableListOf<MutableList<Item>>()
        var current = mutableListOf<Item>()
        var remaining = firstPageCapacity
        pages.add(current)

        for (item in items) {
            val height = itemHeight(item, metrics)
            if (current.isNotEmpty() && height > remaining) {
                current = mutableListOf()
                pages.add(current)
                remaining = continuationCapacity
            }
            current.add(item)
            remaining -= height
        }

        return pages.filter { it.isNotEmpty() }
    }

    /**
     * Largest single-page text size when possible (down to [MIN_TEXT_SIZE]);
     * otherwise [MAX_BODY_SIZE] for multi-page output.
     */
    fun chooseTextSize(
        items: List<Item>,
        hasNotes: Boolean,
        lineHeight: (textSize: Float, isTitle: Boolean) -> Float,
        preferredSize: Float? = null,
    ): Float {
        preferredSize?.let { preferred ->
            return preferred.coerceIn(MIN_TEXT_SIZE, MAX_FONT_SIZE)
        }
        var textSize = MAX_BODY_SIZE
        while (textSize >= MIN_TEXT_SIZE) {
            val metrics = metricsFor(
                textSize = textSize,
                hasNotes = hasNotes,
                titleLineHeight = lineHeight(textSize, true),
                labelLineHeight = lineHeight(textSize, false),
                bodyLineHeight = lineHeight(textSize, false),
            )
            if (fitsOnePage(items, metrics)) {
                return textSize
            }
            textSize -= 1f
        }
        return MAX_BODY_SIZE
    }

    fun layoutPages(
        items: List<Item>,
        metrics: TextMetrics,
    ): List<List<Item>> = if (fitsOnePage(items, metrics)) {
        listOf(items)
    } else {
        paginate(items, metrics)
    }
}
