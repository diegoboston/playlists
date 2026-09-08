package com.playlists.app.ai

/**
 * Parses the line-oriented chart format returned by [OpenAiClient.extractChart].
 * Truncated output is kept: complete sections and complete lyric lines survive.
 */
object ChartTextParser {
    private val HEADER = Regex(
        """^(TITLE|ARTIST|KEY|CAPO|NOTES)\s*:\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val SECTION = Regex("""^\[(.+)]$""")

    fun parse(
        raw: String,
        sourceUrl: String? = null,
        fallbackTitle: String = "",
    ): ChartDraft? {
        val text = unwrapFence(raw)
        if (text.isBlank()) return null

        var title = ""
        var artist: String? = null
        var key: String? = null
        var capo: String? = null
        var notes: String? = null
        val sections = mutableListOf<ChartSection>()
        var currentLabel: String? = null
        val currentLines = mutableListOf<String>()
        var seenSection = false

        fun flush() {
            val label = currentLabel
            if (label != null && currentLines.isNotEmpty()) {
                sections.add(ChartSection(label, currentLines.toList()))
            }
            currentLines.clear()
        }

        for (line in text.lines()) {
            val trimmed = line.trim().trimStart('\uFEFF')
            if (!seenSection) {
                val header = HEADER.matchEntire(trimmed)
                if (header != null) {
                    val value = header.groupValues[2].trim().normalizeOptionalField()
                    when (header.groupValues[1].uppercase()) {
                        "TITLE" -> title = value.orEmpty()
                        "ARTIST" -> artist = value
                        "KEY" -> key = value
                        "CAPO" -> capo = value
                        "NOTES" -> notes = value
                    }
                    continue
                }
            }
            val section = SECTION.matchEntire(trimmed)
            if (section != null) {
                flush()
                currentLabel = section.groupValues[1].trim().ifEmpty { null }
                seenSection = true
                continue
            }
            if (currentLabel != null && trimmed.isNotEmpty()) {
                currentLines.add(trimmed)
            }
        }
        flush()

        val resolvedTitle = title.ifBlank { fallbackTitle.trim() }
        if (resolvedTitle.isEmpty() || sections.isEmpty()) return null
        return ChartDraft(
            title = resolvedTitle,
            artist = artist,
            sourceKey = key,
            key = key,
            capo = capo,
            columns = 1,
            sections = sections,
            notes = notes,
            sourceUrl = sourceUrl,
        )
    }

    private fun unwrapFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed.lineSequence()
            .dropWhile { it.trimStart().startsWith("```") }
            .toList()
            .dropLastWhile { it.trim() == "```" || it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
    }
}
