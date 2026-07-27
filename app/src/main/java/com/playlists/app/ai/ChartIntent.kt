package com.playlists.app.ai

import org.json.JSONObject

enum class ChartSearchMode {
    ChordsAndLyrics,
    LyricsOnly,
    ;

    fun querySuffix(): String = when (this) {
        ChordsAndLyrics -> "chords lyrics"
        LyricsOnly -> "lyrics"
    }
}

data class ChartIntent(
    val action: String,
    val songTitle: String,
    val artist: String?,
    val key: String?,
    val playlistName: String?,
    val transcript: String = "",
    val searchMode: ChartSearchMode = ChartSearchMode.ChordsAndLyrics,
) {
    companion object {
        private val BY_SEPARATOR = Regex("""\s+by\s+""", RegexOption.IGNORE_CASE)

        fun fromJson(
            json: JSONObject,
            transcript: String = "",
            searchMode: ChartSearchMode = ChartSearchMode.ChordsAndLyrics,
        ): ChartIntent? {
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
                searchMode = searchMode,
            )
        }

        /**
         * Parse typed or edited free text like "SONG TITLE by ARTIST" (artist optional).
         * Search suffix is always applied later via [searchQuery] from [searchMode].
         */
        fun fromTypedQuery(
            text: String,
            searchMode: ChartSearchMode,
            playlistName: String? = null,
        ): ChartIntent? {
            val trimmed = text.trim().replace(Regex("\\s+"), " ")
            if (trimmed.isEmpty()) return null
            val parts = BY_SEPARATOR.split(trimmed, limit = 2)
            val title = parts[0].trim()
            if (title.isEmpty()) return null
            val artist = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            return ChartIntent(
                action = "find_chart",
                songTitle = title,
                artist = artist,
                key = null,
                playlistName = playlistName,
                transcript = trimmed,
                searchMode = searchMode,
            )
        }
    }

    fun searchQuery(): String = buildString {
        append(songTitle)
        if (!artist.isNullOrBlank()) append(' ').append(artist)
        append(' ').append(searchMode.querySuffix())
    }.trim()

    /** Editable display for re-search: `Title by Artist` (no chords/lyrics suffix). */
    fun editableQuery(): String = buildString {
        append(songTitle)
        if (!artist.isNullOrBlank()) append(" by ").append(artist)
    }
}
