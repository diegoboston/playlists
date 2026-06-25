package com.playlists.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.playlists.app.R
import com.playlists.app.data.FileType
import com.playlists.app.data.Song
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.SongDeletePrompt
import com.playlists.app.ui.SongTitleWithKey
import com.playlists.app.ui.components.EditSongDialog
import com.playlists.app.ui.components.SongMediaViewer
import com.playlists.app.util.ChartDraftStore
import com.playlists.app.util.SongStoragePaths
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongViewScreen(
    songId: Long,
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onNewKey: (Long) -> Unit,
) {
    var song by remember { mutableStateOf<Song?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SongDeletePrompt?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(songId) {
        song = viewModel.getSong(songId)
        viewModel.recordSongView(songId)
    }

    val loaded = song
    if (loaded == null) return

    val file = SongStoragePaths.resolve(loaded.filePath)
    if (!file.exists()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val fileType = runCatching { FileType.valueOf(loaded.fileType) }.getOrDefault(FileType.IMAGE)
    val hasChartSource = ChartDraftStore.hasChart(loaded.filePath)

    if (showEdit) {
        EditSongDialog(
            song = loaded,
            hasChartSource = hasChartSource,
            onDismiss = { showEdit = false },
            onSave = { title, key, notes ->
                viewModel.updateSong(loaded.id, title, key, notes)
                showEdit = false
                scope.launch { song = viewModel.getSong(songId) }
            },
            onDelete = {
                showEdit = false
                scope.launch {
                    deleteTarget = viewModel.prepareSongDelete(loaded.id)
                }
            },
            onNewKey = { onNewKey(loaded.id) },
        )
    }

    deleteTarget?.let { prompt ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_song)) },
            text = {
                Text(
                    if (prompt.playlistNames.isEmpty()) {
                        stringResource(R.string.delete_song_confirm, prompt.song.title)
                    } else {
                        stringResource(
                            R.string.delete_song_confirm_in_playlists,
                            prompt.song.title,
                            prompt.playlistNames.joinToString(", "),
                        )
                    },
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.deleteSong(prompt.song.id)
                    deleteTarget = null
                    onBack()
                }) {
                    Text(stringResource(R.string.delete_song))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SongTitleWithKey(
                        title = loaded.title,
                        keySignature = loaded.keySignature,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showEdit = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_song))
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
