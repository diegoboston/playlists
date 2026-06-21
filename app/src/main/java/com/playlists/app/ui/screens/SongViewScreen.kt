package com.playlists.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.playlists.app.data.FileType
import com.playlists.app.data.Song
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.components.SongMediaViewer
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongViewScreen(
    songId: Long,
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
) {
    var song by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(songId) {
        song = viewModel.getSong(songId)
    }

    val loaded = song
    if (loaded == null) return

    val file = File(loaded.filePath)
    if (!file.exists()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val fileType = runCatching { FileType.valueOf(loaded.fileType) }.getOrDefault(FileType.IMAGE)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loaded.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        SongMediaViewer(
            file = file,
            fileType = fileType,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}
