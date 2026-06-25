package com.playlists.app.ai

import org.json.JSONObject

data class ChartIntent(
    val action: String,
    val songTitle: String,
    val artist: String?,
    val key: String?,
    val playlistName: String?,
    val transcript: String = "",
) {
    companion object {
        fun fromJson(json: JSONObject, transcript: String = ""): ChartIntent? {
            val title = json.optString("songTitle").trim().ifBlank {
                json.optString("title").trim()
            }
            if (title.isEmpty()) return null
            return ChartIntent(
                action = json.optString("action", "find_chart"),
                songTitle = title,
                artist = json.optString("artist").trim().takeIf { it.isNotEmpty() },
                key = json.optString("key").trim().takeIf { it.isNotEmpty() },
                playlistName = json.optString("playlistName").trim().takeIf { it.isNotEmpty() },
                transcript = transcript,
            )
        }
    }

    fun searchQuery(): String = buildString {
        append(songTitle)
        if (!artist.isNullOrBlank()) append(' ').append(artist)
        append(" chords lyrics")
    }.trim()
}
