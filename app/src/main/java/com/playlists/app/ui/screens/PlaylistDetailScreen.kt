package com.playlists.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.data.Playlist
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.remote.PlayRemoteController
import com.playlists.app.remote.RemotePlayDebugDialog
import com.playlists.app.remote.RemotePlayErrorDialog
import com.playlists.app.remote.RemotePlayErrors
import com.playlists.app.remote.RemotePlayFlowDialog
import com.playlists.app.remote.RemotePlayFlowState
import com.playlists.app.remote.RemotePlayMode
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.SongDisplay
import com.playlists.app.ui.SongTitleWithKey
import com.playlists.app.ui.components.PlaylistColorDialog
import com.playlists.app.ui.components.RemotePlayIconButton
import com.playlists.app.ui.components.TextInputDialog
import com.playlists.app.ui.reorder.DraggableItem
import com.playlists.app.ui.reorder.ReorderDragState
import com.playlists.app.ui.reorder.syncDisplayedKeys
import com.playlists.app.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onPlay: (Long) -> Unit,
    onOpenSong: (Long) -> Unit,
    onNavigateToDuplicate: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlist by remember { mutableStateOf<Playlist?>(null) }
    val entries by viewModel.observePlaylistSongs(playlistId).collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val displayedKeys = remember { mutableStateListOf<String>() }
    val dragState = remember { ReorderDragState() }
    var showRename by remember { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAddSong by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var remoteFlow by remember { mutableStateOf<RemotePlayFlowState?>(null) }
    var remoteStartGeneration by remember { mutableIntStateOf(0) }
    var showRemoteDebug by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        playlist = viewModel.getPlaylist(playlistId)
        AppPrefs.setLastPlaylistId(context, playlistId)
    }

    val remoteRunning by PlayRemoteController.running.collectAsStateWithLifecycle()
    val remoteActiveHere = remoteRunning && PlayRemoteController.isRunningFor(playlistId)

    LaunchedEffect(entries, dragState.draggingKey) {
        syncDisplayedKeys(displayedKeys, dragState.draggingKey, entries.map { "e:${it.id}" })
    }

    LaunchedEffect(entries, remoteRunning, playlistId) {
        if (remoteRunning && PlayRemoteController.isRunningFor(playlistId)) {
            PlayRemoteController.refreshSongs(entries)
        }
    }

    fun cancelRemoteFlow() {
        remoteStartGeneration++
        remoteFlow = null
        scope.launch(Dispatchers.IO) { PlayRemoteController.stop() }
    }

    fun closeRemoteStartedDialog() {
        remoteFlow = null
    }

    fun startRemote(mode: RemotePlayMode) {
        val generation = remoteStartGeneration + 1
        remoteStartGeneration = generation
        remoteFlow = RemotePlayFlowState.Starting(mode)
        scope.launch {
            val list = viewModel.getPlaylistSongs(playlistId)
            if (list.isEmpty()) {
                if (generation == remoteStartGeneration) {
                    remoteFlow = null
                    Toast.makeText(context, R.string.remote_empty, Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val name = playlist?.name.orEmpty()
            val result = withContext(Dispatchers.IO) {
                PlayRemoteController.start(context, playlistId, name, list, mode)
            }
            if (generation != remoteStartGeneration) return@launch
            result
                .onSuccess { url -> remoteFlow = RemotePlayFlowState.Started(url, mode) }
                .onFailure { error ->
                    remoteFlow = null
                    remoteError = RemotePlayErrors.format(error)
                }
        }
    }

    Scaffold(
        topBar = {
            val accentColor = playlist?.colorArgb?.let { Color(it) }
            val titleBg = accentColor ?: MaterialTheme.colorScheme.surface
            val onTitleBg = when {
                accentColor != null && accentColor.luminance() > 0.5f -> Color.Black
                accentColor != null -> Color.White
                else -> MaterialTheme.colorScheme.onSurface
            }
            Surface(shadowElevation = 3.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(titleBg)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = onTitleBg,
                            )
                        }
                        Text(
                            text = playlist?.name.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            color = onTitleBg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showAddSong = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_song))
                        }
                        IconButton(onClick = { onPlay(playlistId) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play))
                        }
                        RemotePlayIconButton(
                            active = remoteActiveHere,
                            onClick = {
                                if (remoteActiveHere) {
                                    PlayRemoteController.stop()
                                    Toast.makeText(context, R.string.remote_stopped, Toast.LENGTH_SHORT).show()
                                } else {
                                    remoteFlow = RemotePlayFlowState.ChooseMode
                                }
                            },
                            onLongClick = if (remoteActiveHere) {
                                { showRemoteDebug = true }
                            } else {
                                null
                            },
                        )
                        IconButton(onClick = { showRename = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_playlist))
                        }
                        IconButton(onClick = { showDuplicate = true }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.duplicate))
                        }
                        IconButton(onClick = { showColor = true }) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = stringResource(R.string.playlist_color),
                            )
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_playlist))
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.empty_playlist))
            }
        } else {
            LazyColumn(
                state = listState,
                userScrollEnabled = !dragState.isDragging,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(displayedKeys.toList(), key = { it }) { key ->
                    val entryId = key.removePrefix("e:").toLongOrNull() ?: return@items
                    val entry = entries.find { it.id == entryId } ?: return@items
                    DraggableItem(
                        isDragging = dragState.draggingKey == key,
                        dragOffset = dragState.currentDragOffset(listState),
                        onTap = { onOpenSong(entry.songId) },
                        onDragStart = { dragState.onDragStart(key, listState) },
                        onDrag = { delta -> dragState.onDrag(delta, listState, displayedKeys) },
                        onDragEnd = {
                            dragState.finishDrag {
                                val ids = displayedKeys.mapNotNull { it.removePrefix("e:").toLongOrNull() }
                                viewModel.reorderPlaylistSongs(playlistId, ids)
                            }
                        },
                        onDragCancel = { dragState.cancelDrag() },
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
            onAddPlaceholder = { title ->
                viewModel.addPlaceholderToPlaylist(playlistId, title) {
                    showAddSong = false
                }
            },
        )
    }

    remoteError?.let { message ->
        RemotePlayErrorDialog(message = message, onDismiss = { remoteError = null })
    }

    remoteFlow?.let { flow ->
        RemotePlayFlowDialog(
            state = flow,
            onCancel = { cancelRemoteFlow() },
            onCloseStarted = { closeRemoteStartedDialog() },
            onSelectMode = { mode -> startRemote(mode) },
        )
    }

    if (showRemoteDebug) {
        RemotePlayDebugDialog(onDismiss = { showRemoteDebug = false })
    }
}

@Composable
private fun PlaylistSongRow(
    entry: PlaylistSongWithDetails,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SongTitleWithKey(
                    title = entry.title,
                    keySignature = entry.keySignature,
                    isPlaceholder = entry.isPlaceholder,
                )
                val notes = SongDisplay.notesLine(entry.notes)
                if (notes.isNotEmpty()) {
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AddSongDialog(
    viewModel: PlaylistsViewModel,
    onDismiss: () -> Unit,
    onAdd: (Long) -> Unit,
    onAddPlaceholder: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.playlists.app.data.Song>>(emptyList()) }

    LaunchedEffect(query) {
        results = viewModel.searchSongs(query)
    }

    val trimmedQuery = query.trim()
    val showPlaceholder = trimmedQuery.isNotEmpty()

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
                    if (showPlaceholder) {
                        item(key = "placeholder") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddPlaceholder(trimmedQuery) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.add_placeholder, trimmedQuery),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    items(results, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(song.id) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SongTitleWithKey(
                                title = song.title,
                                keySignature = song.keySignature,
                                isPlaceholder = song.isPlaceholder,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
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
