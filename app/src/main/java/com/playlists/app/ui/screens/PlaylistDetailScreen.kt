package com.playlists.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.data.Playlist
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.remote.PlayRemoteController
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.SongDisplay
import com.playlists.app.ui.components.PlaylistColorDialog
import com.playlists.app.ui.components.TextInputDialog
import com.playlists.app.ui.reorder.DraggableItem
import com.playlists.app.ui.reorder.handleLazyListDrag
import com.playlists.app.ui.reorder.syncDisplayedKeys
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onPlay: (Long) -> Unit,
    onNavigateToDuplicate: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlist by remember { mutableStateOf<Playlist?>(null) }
    val entries by viewModel.observePlaylistSongs(playlistId).collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val displayedKeys = remember { mutableStateListOf<String>() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAddSong by remember { mutableStateOf(false) }
    var remoteUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId) {
        playlist = viewModel.getPlaylist(playlistId)
    }

    val remoteRunning by PlayRemoteController.running.collectAsStateWithLifecycle()

    LaunchedEffect(entries, draggingKey) {
        syncDisplayedKeys(displayedKeys, draggingKey, entries.map { "e:${it.id}" })
    }

    LaunchedEffect(remoteRunning, playlistId) {
        remoteUrl = if (remoteRunning && PlayRemoteController.isRunningFor(playlistId)) {
            PlayRemoteController.currentUrl()
        } else {
            null
        }
    }

    LaunchedEffect(entries, remoteRunning, playlistId) {
        if (remoteRunning && PlayRemoteController.isRunningFor(playlistId)) {
            PlayRemoteController.refreshSongs(entries)
        }
    }

    DisposableEffect(playlistId) {
        onDispose {
            if (PlayRemoteController.isRunningFor(playlistId)) {
                PlayRemoteController.stop()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = playlist?.name.orEmpty(),
                        color = playlist?.colorArgb?.let { Color(it) } ?: Color.Unspecified,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSong = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_song))
                    }
                    IconButton(onClick = { onPlay(playlistId) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play))
                    }
                    IconButton(onClick = {
                        if (PlayRemoteController.isRunningFor(playlistId)) {
                            PlayRemoteController.currentUrl()?.let { url ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                            return@IconButton
                        }
                        scope.launch {
                            val list = viewModel.getPlaylistSongs(playlistId)
                            if (list.isEmpty()) {
                                Toast.makeText(context, R.string.remote_empty, Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            val name = playlist?.name.orEmpty()
                            PlayRemoteController.start(context, playlistId, name, list)
                                .onSuccess { url ->
                                    remoteUrl = url
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    Toast.makeText(context, R.string.remote_started, Toast.LENGTH_SHORT).show()
                                }
                                .onFailure { error ->
                                    val message = when {
                                        error.message?.contains("LAN IP") == true ->
                                            context.getString(R.string.remote_no_network)
                                        else -> context.getString(R.string.remote_failed, error.message ?: "unknown")
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                        }
                    }) {
                        Icon(Icons.Default.Wifi, contentDescription = stringResource(R.string.remote_play))
                    }
                    if (PlayRemoteController.isRunningFor(playlistId)) {
                        IconButton(onClick = {
                            PlayRemoteController.stop()
                            remoteUrl = null
                        }) {
                            Text(stringResource(R.string.remote_stop), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_playlist))
                    }
                    IconButton(onClick = { showDuplicate = true }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.duplicate))
                    }
                    IconButton(onClick = { showColor = true }) {
                        if (playlist?.colorArgb != null) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(playlist!!.colorArgb!!)),
                            )
                        } else {
                            Icon(
                                Icons.Default.ColorLens,
                                contentDescription = stringResource(R.string.playlist_color),
                            )
                        }
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_playlist))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (remoteUrl != null) {
                Text(
                    text = stringResource(R.string.remote_url_label, remoteUrl!!),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(12.dp),
                )
            }
            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.empty_playlist))
                }
            } else {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = draggingKey == null,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(displayedKeys.toList(), key = { it }) { key ->
                        val entryId = key.removePrefix("e:").toLongOrNull() ?: return@items
                        val entry = entries.find { it.id == entryId } ?: return@items
                        DraggableItem(
                            key = key,
                            enabled = draggingKey == null || draggingKey == key,
                            draggingKey = draggingKey,
                            onDragStart = { draggingKey = it },
                            onDrag = { dragKey, visualTop ->
                                handleLazyListDrag(listState, dragKey, visualTop, displayedKeys)
                            },
                            onDragEnd = {
                                draggingKey = null
                                val ids = displayedKeys.mapNotNull { it.removePrefix("e:").toLongOrNull() }
                                viewModel.reorderPlaylistSongs(playlistId, ids)
                            },
                        ) {
                            PlaylistSongRow(
                                entry = entry,
                                onRemove = { viewModel.removeSongFromPlaylist(entry.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        TextInputDialog(
            title = stringResource(R.string.rename_playlist),
            initialValue = playlist?.name.orEmpty(),
            confirmLabel = stringResource(R.string.save),
            onDismiss = { showRename = false },
            onConfirm = { name ->
                showRename = false
                viewModel.renamePlaylist(playlistId, name)
                playlist = playlist?.copy(name = name)
            },
        )
    }

    if (showDuplicate) {
        TextInputDialog(
            title = stringResource(R.string.duplicate),
            initialValue = stringResource(R.string.duplicate_default_name, playlist?.name.orEmpty()),
            confirmLabel = stringResource(R.string.create),
            onDismiss = { showDuplicate = false },
            onConfirm = { name ->
                showDuplicate = false
                viewModel.duplicatePlaylist(playlistId, name) { newId ->
                    onNavigateToDuplicate(newId)
                }
            },
        )
    }

    if (showColor) {
        PlaylistColorDialog(
            currentColor = playlist?.colorArgb,
            onDismiss = { showColor = false },
            onColorSelected = { color ->
                showColor = false
                viewModel.setPlaylistColor(playlistId, color)
                playlist = playlist?.copy(colorArgb = color)
            },
        )
    }

    if (showDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_playlist)) },
            text = { Text(stringResource(R.string.delete_playlist_confirm, playlist?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    if (PlayRemoteController.isRunningFor(playlistId)) PlayRemoteController.stop()
                    viewModel.deletePlaylist(playlistId)
                    onBack()
                }) {
                    Text(stringResource(R.string.delete_playlist))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showAddSong) {
        AddSongDialog(
            viewModel = viewModel,
            onDismiss = { showAddSong = false },
            onAdd = { songId ->
                viewModel.addSongToPlaylist(playlistId, songId)
                showAddSong = false
            },
        )
    }
}

@Composable
private fun PlaylistSongRow(
    entry: PlaylistSongWithDetails,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isDeleted) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val line = SongDisplay.playlistLine(entry.keySignature, entry.notes)
                if (line.isNotEmpty()) {
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onRemove) {
                Text(stringResource(R.string.remove))
            }
        }
    }
}

@Composable
private fun AddSongDialog(
    viewModel: PlaylistsViewModel,
    onDismiss: () -> Unit,
    onAdd: (Long) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.playlists.app.data.Song>>(emptyList()) }

    LaunchedEffect(query) {
        results = viewModel.searchSongs(query)
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_song)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_songs)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results, key = { it.id }) { song ->
                        Text(
                            text = song.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(song.id) }
                                .padding(8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
