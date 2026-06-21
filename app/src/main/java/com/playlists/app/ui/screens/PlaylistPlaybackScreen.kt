package com.playlists.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.playlists.app.R
import com.playlists.app.data.FileType
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.components.SongMediaViewer
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPlaybackScreen(
    playlistId: Long,
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
) {
    var playlistName by remember { mutableStateOf("") }
    var songs by remember { mutableStateOf<List<PlaylistSongWithDetails>>(emptyList()) }
    var pdfPage by remember { mutableIntStateOf(0) }
    var pdfPageCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(playlistId) {
        playlistName = viewModel.getPlaylist(playlistId)?.name.orEmpty()
        songs = viewModel.getPlaylistSongs(playlistId)
        if (songs.isEmpty()) onBack()
    }

    if (songs.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { songs.size })

    LaunchedEffect(pagerState.currentPage) {
        pdfPage = 0
        pdfPageCount = 0
    }

    val indicator = buildString {
        append(
            stringResource(
                R.string.playback_song_indicator,
                pagerState.currentPage + 1,
                songs.size,
                songs[pagerState.currentPage].title,
            ),
        )
        if (pdfPageCount > 1) {
            append(" · ")
            append(stringResource(R.string.playback_page_indicator, pdfPage + 1, pdfPageCount))
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { index ->
            val entry = songs[index]
            val file = File(entry.filePath)
            if (!file.exists()) return@HorizontalPager
            val fileType = runCatching { FileType.valueOf(entry.fileType) }.getOrDefault(FileType.IMAGE)
            SongMediaViewer(
                file = file,
                fileType = fileType,
                modifier = Modifier.fillMaxSize(),
                onPdfPageChanged = { page, count ->
                    if (pagerState.currentPage == index) {
                        pdfPage = page
                        pdfPageCount = count
                    }
                },
            )
        }
    }
}
