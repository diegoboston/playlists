package com.playlists.app.util

sealed class SharePayload {
    data class FileImport(val pending: PendingImport) : SharePayload()

    /** Chord-chart web page — run AI extract instead of downloading a file. */
    data class ChartUrl(val url: String, val titleHint: String) : SharePayload()
}

data class PendingChartImport(
    val url: String,
    val titleHint: String,
    /** When set, saved song is also added to this playlist; otherwise archive only. */
    val playlistId: Long?,
)
