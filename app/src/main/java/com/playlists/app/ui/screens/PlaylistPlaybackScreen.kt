package com.playlists.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.playlists.app.R
import com.playlists.app.data.FileType
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.ui.PdfHelper
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.SongDisplay
import com.playlists.app.ui.SongTitleWithKey
import com.playlists.app.ui.components.PlaybackStage
import com.playlists.app.ui.components.SongMediaViewer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PlaybackFrame(
    val songIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val entry: PlaylistSongWithDetails,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPlaybackScreen(
    playlistId: Long,
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
) {
    var frames by remember { mutableStateOf<List<PlaybackFrame>>(emptyList()) }
    var songCount by remember { mutableIntStateOf(0) }
    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(playlistId) {
        val songs = viewModel.getPlaylistSongs(playlistId)
        if (songs.isEmpty()) {
            onBack()
            return@LaunchedEffect
        }
        songCount = songs.size
        frames = withContext(Dispatchers.IO) { buildPlaybackFrames(songs) }
        if (frames.isEmpty()) {
            onBack()
        } else {
            currentIndex = 0
        }
    }

    if (frames.isEmpty()) return

    val frame = frames[currentIndex.coerceIn(frames.indices)]

    LaunchedEffect(frame.entry.songId) {
        viewModel.recordSongView(frame.entry.songId)
    }

    val indicator = buildString {
        append(
            stringResource(
                R.string.playback_song_indicator,
                frame.songIndex + 1,
                songCount,
                SongDisplay.titleWithKey(
                    frame.entry.title,
                    frame.entry.keySignature,
                    frame.entry.isPlaceholder,
                ),
            ),
        )
        if (frame.pageCount > 1) {
            append(" · ")
            append(stringResource(R.string.playback_page_indicator, frame.pageIndex + 1, frame.pageCount))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = indicator,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        PlaybackStage(
            contentKey = currentIndex,
            canGoPrev = currentIndex > 0,
            canGoNext = currentIndex < frames.lastIndex,
            onPrev = { if (currentIndex > 0) currentIndex-- },
            onNext = { if (currentIndex < frames.lastIndex) currentIndex++ },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .focusable(),
        ) {
            val item = frame
            val file = File(item.entry.filePath)
            if (!file.exists()) return@PlaybackStage
            val fileType = runCatching { FileType.valueOf(item.entry.fileType) }
                .getOrDefault(FileType.IMAGE)
            SongMediaViewer(
                file = file,
                fileType = fileType,
                modifier = Modifier.fillMaxSize(),
                pdfPageIndex = if (fileType == FileType.PDF) item.pageIndex else null,
                enableZoom = false,
            )
        }
    }
}

private fun buildPlaybackFrames(songs: List<PlaylistSongWithDetails>): List<PlaybackFrame> {
    val frames = mutableListOf<PlaybackFrame>()
    songs.forEachIndexed { songIndex, entry ->
        val file = File(entry.filePath)
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
