package com.playlists.app.ai

import org.json.JSONArray
import org.json.JSONObject

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
            val sourceKey = json.optString("sourceKey").trim().takeIf { it.isNotEmpty() }
            val key = json.optString("key").trim().takeIf { it.isNotEmpty() } ?: sourceKey
            return ChartDraft(
                title = title,
                artist = json.optString("artist").trim().takeIf { it.isNotEmpty() },
                sourceKey = sourceKey,
                key = key,
                capo = json.optString("capo").trim().takeIf { it.isNotEmpty() },
                columns = json.optInt("columns", 1).coerceIn(1, 2),
                sections = sections,
                notes = json.optString("notes").trim().takeIf { it.isNotEmpty() },
                sourceUrl = json.optString("sourceUrl").trim().takeIf { it.isNotEmpty() },
            )
        }
    }
}
