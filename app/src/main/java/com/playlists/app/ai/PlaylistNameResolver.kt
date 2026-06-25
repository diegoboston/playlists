package com.playlists.app.ai

import com.playlists.app.data.Playlist

data class PlaylistMatch(
    val playlist: Playlist,
    val score: Double,
)

object PlaylistNameResolver {
    fun resolve(name: String?, playlists: List<Playlist>, defaultPlaylistId: Long?): Playlist? {
        if (name.isNullOrBlank()) {
            return defaultPlaylistId?.let { id -> playlists.find { it.id == id } }
        }
        val normQuery = normalize(name)
        if (normQuery.isEmpty()) {
            return defaultPlaylistId?.let { id -> playlists.find { it.id == id } }
        }
        val ranked = playlists.map { playlist ->
            PlaylistMatch(playlist, score(normQuery, normalize(playlist.name)))
        }.sortedByDescending { it.score }
        val best = ranked.firstOrNull()?.takeIf { it.score >= 0.5 }?.playlist
        return best ?: defaultPlaylistId?.let { id -> playlists.find { it.id == id } }
    }

    fun ambiguousMatches(name: String, playlists: List<Playlist>): List<PlaylistMatch> {
        val normQuery = normalize(name)
        return playlists.map { playlist ->
            PlaylistMatch(playlist, score(normQuery, normalize(playlist.name)))
        }.filter { it.score >= 0.5 }
            .sortedByDescending { it.score }
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun score(query: String, candidate: String): Double {
        if (query.isEmpty() || candidate.isEmpty()) return 0.0
        if (query == candidate) return 1.0
        if (candidate.contains(query) || query.contains(candidate)) return 0.9
        val qTokens = query.split(' ').filter { it.length > 1 }
        val cTokens = candidate.split(' ').filter { it.length > 1 }
        if (qTokens.isEmpty() || cTokens.isEmpty()) return 0.0
        val overlap = qTokens.count { token ->
            cTokens.any { it == token || it.startsWith(token) || token.startsWith(it) }
        }
        return overlap.toDouble() / maxOf(qTokens.size, cTokens.size)
    }
}
