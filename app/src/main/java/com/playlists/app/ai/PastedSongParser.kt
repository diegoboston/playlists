package com.playlists.app.ai

import org.json.JSONArray
import org.json.JSONObject

/** The model classifies lines; song text is always copied from the original input. */
object PastedSongParser {
    fun lines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Explicit IDs avoid relying on the model to count a positional array. */
    fun indexedInput(lines: List<String>): JSONObject = JSONObject().apply {
        lines.forEachIndexed { index, line -> put((index + 1).toString(), line) }
    }

    fun responseFormat(lineCount: Int): JSONObject {
        val ids = (1..lineCount).map { it.toString() }
        val lineProperties = JSONObject().apply {
            ids.forEach { put(it, JSONObject().put("\$ref", "#/\$defs/lineKind")) }
        }
        val properties = JSONObject().apply {
            listOf("title", "artist", "key", "capo").forEach {
                put(it, JSONObject().put("type", "string"))
            }
            put("lineKinds", JSONObject().put("type", "object")
                .put("properties", lineProperties).put("required", JSONArray(ids))
                .put("additionalProperties", false))
        }
        val schema = JSONObject().put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(listOf("title", "artist", "key", "capo", "lineKinds")))
            .put("additionalProperties", false)
            .put("\$defs", JSONObject().put("lineKind", JSONObject().put("type", "string")
                .put("enum", JSONArray(listOf("lyrics", "chords", "section", "metadata", "clutter")))))
        return JSONObject().put("type", "json_schema").put("json_schema", JSONObject()
            .put("name", "pasted_song_classification").put("strict", true).put("schema", schema))
    }

    fun parse(text: String, plan: JSONObject, mode: ChartSearchMode, log: (String) -> Unit): ChartDraft {
        val lines = lines(text)
        val kinds = plan.getJSONObject("lineKinds")
        log("Classification coverage: ${kinds.length()}/${lines.size} nonempty input lines.")
        require(kinds.length() == lines.size && lines.indices.all { kinds.has((it + 1).toString()) }) {
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
            when (val kind = kinds.getString((index + 1).toString())) {
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
                "metadata" -> if (isSupportedMetadata(line, plan)) {
                    removed.add(index + 1)
                } else {
                    // A model label alone must not discard an ordinary lyric sentence.
                    current.add(if (mode == ChartSearchMode.LyricsOnly) {
                        CHORD_MARKER.replace(line, "").trim()
                    } else CHORD_MARKER.replace(line) { "<${it.groupValues[1]}>" })
                    kept++
                    log("Preserved line ${index + 1}: metadata label lacked a matching header, title, artist, or credit.")
                }
                "clutter" -> removed.add(index + 1)
                else -> error("Invalid classification '$kind' at line ${index + 1}. Nothing was imported.")
            }
        }
        flush()
        require(sections.isNotEmpty()) { "No song lines found. Nothing was imported." }
        log("Line classification complete: ${lines.size}/${lines.size}. Preserved $kept song/section lines; omitted ${removed.size} metadata/clutter lines.")
        if (removed.isNotEmpty()) {
            log("Metadata/clutter line numbers: ${removed.joinToString(", ")}")
            log("Omitted line classifications: ${removed.joinToString { "$it=${kinds.getString(it.toString())}" }}")
        }
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

    private fun isSupportedMetadata(line: String, plan: JSONObject): Boolean {
        if (METADATA_HEADER.containsMatchIn(line) || CREDIT_LINE.containsMatchIn(line)) return true
        val title = plan.optString("title").normalizeOptionalField()
        val artist = plan.optString("artist").normalizeOptionalField()
        if (title != null && artist != null && (
            line.equals("$title by $artist", ignoreCase = true) ||
            line.equals("$title - $artist", ignoreCase = true) ||
            line.equals("$artist - $title", ignoreCase = true)
        )) return true
        return listOf("title", "artist", "key", "capo").any { field ->
            val value = plan.optString(field).normalizeOptionalField()
            value != null && line.equals(value, ignoreCase = true)
        }
    }

    private val METADATA_HEADER = Regex(
        """^(?:title|artist|song|album|key|capo|tuning|tempo|bpm|author|composer|credits?)\s*:""",
        RegexOption.IGNORE_CASE,
    )
    private val CREDIT_LINE = Regex(
        """^(?:(?:written|composed|arranged|transcribed|submitted)\s+by\b|(?:words|music|lyrics|chords)\s+(?:and\s+(?:words|music|lyrics)\s+)?by\b|copyright\b|©)""",
        RegexOption.IGNORE_CASE,
    )

    private const val CHORD_PATTERN = "[A-G](?:#|b)?(?:maj|min|m|dim|aug|sus|add)?\\d*(?:/[A-G](?:#|b)?)?"
    private val CHORD = Regex(CHORD_PATTERN)
    private val CHORD_MARKER = Regex("[<\\[]($CHORD_PATTERN)[>\\]]")
}
