package com.playlists.app.util

import com.playlists.app.data.Song

object QuickstartMatcher {
    data class MatchResult(
        val line: String,
        val song: Song?,
    )

    fun matchLines(lines: List<String>, archive: List<Song>): List<MatchResult> {
        val normalizedArchive = archive.map { it to normalize(it.title) }
        return lines.map { line ->
            val normLine = normalize(line)
            if (normLine.isEmpty()) {
                MatchResult(line, null)
            } else {
                val best = normalizedArchive
                    .map { (song, normTitle) ->
                        song to score(normLine, normTitle, song.keySignature)
                    }
                    .maxByOrNull { it.second }
                val song = best?.takeIf { it.second >= 0.35 }?.first
                MatchResult(line, song)
            }
        }
    }

    fun matchedSongIds(results: List<MatchResult>): List<Long> =
        results.mapNotNull { it.song?.id }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun score(line: String, title: String, key: String): Double {
        if (title.isEmpty()) return 0.0
        if (line == title) return 1.0
        if (line.contains(title) || title.contains(line)) {
            return 0.85 + minOf(line.length, title.length).toDouble() / 100.0
        }
        val lineTokens = line.split(' ').filter { it.length > 1 }
        val titleTokens = title.split(' ').filter { it.length > 1 }
        if (lineTokens.isEmpty() || titleTokens.isEmpty()) return 0.0
        val overlap = lineTokens.count { token -> titleTokens.any { it == token || it.startsWith(token) || token.startsWith(it) } }
        var score = overlap.toDouble() / maxOf(lineTokens.size, titleTokens.size)
        val normKey = normalize(key)
        if (normKey.isNotEmpty() && line.contains(normKey)) score += 0.15
        return score.coerceAtMost(1.0)
    }
}
