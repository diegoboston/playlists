package com.playlists.app.ai

import org.json.JSONArray
import org.json.JSONObject

object AiJsonHelper {
    fun parseObject(raw: String): JSONObject? {
        val trimmed = raw.trim()
        val fenced = Regex("""```(?:json)?\s*([\s\S]*?)```""").find(trimmed)?.groupValues?.get(1)?.trim()
        val candidate = fenced ?: trimmed
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(candidate.substring(start, end + 1)) }.getOrNull()
    }

    /**
     * Recover title + complete `sections` objects when the model is cut off
     * (truncated JSON / OpenAI `content_filter`) mid-response.
     */
    fun salvageChartJson(raw: String): JSONObject? {
        val title = jsonStringField(raw, "title")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val sections = completeSectionObjects(raw)
        if (sections.isEmpty()) return null
        val json = JSONObject().put("title", title).put("columns", 1)
        jsonStringField(raw, "artist")?.trim()?.takeIf { it.isNotEmpty() }?.let { json.put("artist", it) }
        val arr = JSONArray()
        sections.forEach { arr.put(it) }
        json.put("sections", arr)
        return json
    }

    private fun jsonStringField(raw: String, name: String): String? {
        val match = Regex(""""$name"\s*:\s*"((?:\\.|[^"\\])*)"""").find(raw) ?: return null
        return runCatching {
            JSONObject("{\"v\":\"${match.groupValues[1]}\"}").optString("v")
        }.getOrNull()
    }

    private fun completeSectionObjects(raw: String): List<JSONObject> {
        val found = mutableListOf<JSONObject>()
        var i = 0
        while (i < raw.length) {
            val start = raw.indexOf('{', i)
            if (start < 0) break
            val end = matchingBrace(raw, start)
            if (end == null) {
                i = start + 1
                continue
            }
            val slice = raw.substring(start, end + 1)
            val obj = runCatching { JSONObject(slice) }.getOrNull()
            val lines = obj?.optJSONArray("lines")
            if (obj != null && obj.has("label") && lines != null && lines.length() > 0) {
                found.add(obj)
                i = end + 1
            } else {
                i = start + 1
            }
        }
        return found
    }

    internal fun matchingBrace(s: String, openIdx: Int): Int? {
        var depth = 0
        var i = openIdx
        var inString = false
        var escape = false
        while (i < s.length) {
            val c = s[i]
            when {
                inString && escape -> escape = false
                inString && c == '\\' -> escape = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    fun playlistsContext(playlists: List<Pair<Long, String>>, currentPlaylistId: Long?): String {
        val arr = JSONArray()
        playlists.forEach { (id, name) ->
            arr.put(
                JSONObject()
                    .put("id", id)
                    .put("name", name),
            )
        }
        return JSONObject()
            .put("playlists", arr)
            .put("currentPlaylistId", currentPlaylistId ?: JSONObject.NULL)
            .toString()
    }
}
