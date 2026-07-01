package com.playlists.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.playlists.app.R
import com.playlists.app.data.FileType
import com.playlists.app.ui.PlaybackFrame
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.SongDisplay
import com.playlists.app.ui.buildPlaybackFrames
import com.playlists.app.ui.components.PlaybackSongMedia
import com.playlists.app.ui.components.PlaybackStage
import com.playlists.app.util.SongShare
import com.playlists.app.util.SongStoragePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPlaybackScreen(
    playlistId: Long,
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
) {
    var frames by remember { mutableStateOf<List<PlaybackFrame>>(emptyList()) }
    var songCount by remember { mutableIntStateOf(0) }
    var currentIndex by rememberSaveable(playlistId) { mutableIntStateOf(0) }
    var restartTick by remember { mutableIntStateOf(0) }

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
            currentIndex = currentIndex.coerceIn(0, frames.lastIndex)
        }
    }

    if (frames.isEmpty()) return

    val context = LocalContext.current
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
                SongDisplay.adjustedSongTitle(
                    frame.entry.title,
                    frame.entry.keySignature,
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
                actions = {
                    IconButton(onClick = {
                        val item = frame
                        val file = SongStoragePaths.resolve(item.entry.filePath)
                        val fileType = runCatching { FileType.valueOf(item.entry.fileType) }
                            .getOrDefault(FileType.IMAGE)
                        SongShare.share(context, file, item.entry.title, fileType)
                    }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.share_song),
                        )
                    }
                    IconButton(onClick = {
                        currentIndex = 0
                        restartTick++
                    }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.playback_restart),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PlaybackStage(
            contentKey = restartTick to currentIndex,
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
            val file = SongStoragePaths.resolve(item.entry.filePath)
            val fileType = runCatching { FileType.valueOf(item.entry.fileType) }
                .getOrDefault(FileType.IMAGE)
            PlaybackSongMedia(
                file = file,
                fileType = fileType,
                pageIndex = item.pageIndex,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
