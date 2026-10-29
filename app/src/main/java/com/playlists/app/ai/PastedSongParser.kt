package com.playlists.app.ai

import org.json.JSONObject

/** The model classifies lines; song text is always copied from the original input. */
object PastedSongParser {
    fun lines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun parse(text: String, plan: JSONObject, mode: ChartSearchMode, log: (String) -> Unit): ChartDraft {
        val lines = lines(text)
        val kinds = plan.getJSONArray("lineKinds")
        log("Classification coverage: ${kinds.length()}/${lines.size} nonempty input lines.")
        require(kinds.length() == lines.size) {
            "Incomplete line classification: received ${kinds.length()} of ${lines.size} lines. Nothing was imported."
        }
        val sections = mutableListOf<ChartSection>()
        var label = "Song"
        val current = mutableListOf<String>()
        var kept = 0
        val removed = mutableListOf<Int>()
        fun flush() {
            if (current.isNotEmpty()) sections.add(ChartSection(label, current.toList()))
            current.clear()
        }
        lines.forEachIndexed { index, line ->
            when (val kind = kinds.getString(index)) {
                "lyrics" -> {
                    // Preserve every occurrence, including repeated verses and choruses.
                    current.add(if (mode == ChartSearchMode.LyricsOnly) {
                        CHORD_MARKER.replace(line, "").trim()
                    } else CHORD_MARKER.replace(line) { "<${it.groupValues[1]}>" })
                    kept++
                }
                "chords" -> if (mode != ChartSearchMode.LyricsOnly) {
                    require(line.split(Regex("\\s+")).all { CHORD.matches(it.trim('<', '>', '[', ']')) }) {
                        "Could not safely format chord line ${index + 1}. Nothing was imported."
                    }
                    current.add(line.split(Regex("\\s+")).joinToString(" ") { "<${it.trim('<', '>', '[', ']')}>" })
                    kept++
                }
                "section" -> { flush(); label = line.trim('[', ']'); kept++ }
                "metadata", "clutter" -> removed.add(index + 1)
                else -> error("Invalid classification '$kind' at line ${index + 1}. Nothing was imported.")
            }
        }
        flush()
        require(sections.isNotEmpty()) { "No song lines found. Nothing was imported." }
        log("Line classification complete: ${lines.size}/${lines.size}. Preserved $kept song/section lines; omitted ${removed.size} metadata/clutter lines.")
        if (removed.isNotEmpty()) log("Metadata/clutter line numbers: ${removed.joinToString(", ")}")
        if (mode == ChartSearchMode.LyricsOnly) log("Lyrics-only mode: chord-only lines and bracketed chord markers omitted.")
        val key = if (mode == ChartSearchMode.LyricsOnly) null else plan.optString("key").normalizeOptionalField()
        return ChartDraft(
            title = plan.optString("title").normalizeOptionalField() ?: "Untitled song",
            artist = plan.optString("artist").normalizeOptionalField(),
            sourceKey = key, key = key,
            capo = if (mode == ChartSearchMode.LyricsOnly) null else plan.optString("capo").normalizeOptionalField(),
            columns = 1, sections = sections, notes = null, sourceUrl = null,
        )
    }

    private const val CHORD_PATTERN = "[A-G](?:#|b)?(?:maj|min|m|dim|aug|sus|add)?\\d*(?:/[A-G](?:#|b)?)?"
    private val CHORD = Regex(CHORD_PATTERN)
    private val CHORD_MARKER = Regex("[<\\[]($CHORD_PATTERN)[>\\]]")
}
