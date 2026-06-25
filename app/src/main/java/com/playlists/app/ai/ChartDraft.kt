package com.playlists.app.ai

import org.json.JSONArray
import org.json.JSONObject

/** Blank or JSON-null strings from [JSONObject.optString] become Kotlin null. */
internal fun String?.normalizeOptionalField(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

data class ChartSection(
    val label: String,
    val lines: List<String>,
)

data class ChartDraft(
    val title: String,
    val artist: String?,
    val sourceKey: String?,
    val key: String?,
    val capo: String?,
    val columns: Int,
    val sections: List<ChartSection>,
    val notes: String?,
    val sourceUrl: String?,
) {
    fun withTargetKey(targetKey: String): ChartDraft = copy(key = targetKey)

    fun toJson(): JSONObject {
        val json = JSONObject()
            .put("title", title)
            .put("columns", columns)
        artist?.let { json.put("artist", it) }
        sourceKey?.let { json.put("sourceKey", it) }
        key?.let { json.put("key", it) }
        capo?.let { json.put("capo", it) }
        notes?.let { json.put("notes", it) }
        sourceUrl?.let { json.put("sourceUrl", it) }
        val sectionsArr = JSONArray()
        sections.forEach { section ->
            sectionsArr.put(
                JSONObject()
                    .put("label", section.label)
                    .put("lines", JSONArray(section.lines)),
            )
        }
        json.put("sections", sectionsArr)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): ChartDraft? {
            val title = json.optString("title").trim()
            if (title.isEmpty()) return null
            val sections = mutableListOf<ChartSection>()
            val arr = json.optJSONArray("sections") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val section = arr.optJSONObject(i) ?: continue
                val label = section.optString("label").trim()
                val linesArr = section.optJSONArray("lines") ?: JSONArray()
                val lines = buildList {
                    for (j in 0 until linesArr.length()) {
                        val line = linesArr.optString(j).trim()
                        if (line.isNotEmpty()) add(line)
                    }
                }
                if (lines.isNotEmpty()) {
                    sections.add(ChartSection(label, lines))
                }
            }
            if (sections.isEmpty()) return null
            val sourceKey = json.optString("sourceKey").normalizeOptionalField()
            val key = json.optString("key").normalizeOptionalField() ?: sourceKey
            return ChartDraft(
                title = title,
                artist = json.optString("artist").normalizeOptionalField(),
                sourceKey = sourceKey,
                key = key,
                capo = json.optString("capo").normalizeOptionalField(),
                columns = json.optInt("columns", 1).coerceIn(1, 2),
                sections = sections,
                notes = json.optString("notes").normalizeOptionalField(),
                sourceUrl = json.optString("sourceUrl").normalizeOptionalField(),
            )
        }
    }
}
