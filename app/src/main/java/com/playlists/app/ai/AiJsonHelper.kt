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

    fun playlistsContext(playlists: List<com.playlists.app.data.Playlist>, currentPlaylistId: Long?): String {
        val arr = JSONArray()
        playlists.forEach { playlist ->
            arr.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name),
            )
        }
        return JSONObject()
            .put("playlists", arr)
            .put("currentPlaylistId", currentPlaylistId ?: JSONObject.NULL)
            .toString()
    }
}
