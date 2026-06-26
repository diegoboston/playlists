package com.playlists.app.ui

import com.playlists.app.data.FileType
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.util.SongStoragePaths

data class PlaybackFrame(
    val songIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val entry: PlaylistSongWithDetails,
)

data class TocEntry(
    val title: String,
    val keySignature: String,
)

fun buildTocEntries(songs: List<PlaylistSongWithDetails>): List<TocEntry> =
    songs.map { TocEntry(title = it.title, keySignature = it.keySignature) }

fun buildPlaybackFrames(songs: List<PlaylistSongWithDetails>): List<PlaybackFrame> {
    val frames = mutableListOf<PlaybackFrame>()
    songs.forEachIndexed { songIndex, entry ->
        val file = SongStoragePaths.resolve(entry.filePath)
        if (!file.exists()) return@forEachIndexed
        val fileType = runCatching { FileType.valueOf(entry.fileType) }.getOrDefault(FileType.IMAGE)
        val pageCount = when (fileType) {
            FileType.IMAGE -> 1
            FileType.PDF -> PdfHelper.pageCount(file, fileType).coerceAtLeast(1)
        }
        repeat(pageCount) { pageIndex ->
            frames.add(
                PlaybackFrame(
                    songIndex = songIndex,
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    entry = entry,
                ),
            )
        }
    }
    return frames
}

fun countMissingPlaylistFiles(songs: List<PlaylistSongWithDetails>): Int =
    songs.count { !SongStoragePaths.resolve(it.filePath).exists() }

fun sanitizeExportFilename(name: String): String {
    val trimmed = name.trim().ifBlank { "playlist" }
    val sanitized = trimmed.replace(Regex("""[^\w\s.-]"""), "_").replace(Regex("""\s+"""), "_")
    return sanitized.trim('_').ifBlank { "playlist" }
}
