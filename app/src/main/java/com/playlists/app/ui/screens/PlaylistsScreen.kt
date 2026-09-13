package com.playlists.app.ui.screens

import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.data.Playlist
import com.playlists.app.remote.PlayRemoteController
import com.playlists.app.remote.RemotePlayStartPhase
import com.playlists.app.remote.RemotePlayMode
import com.playlists.app.remote.RemotePlayStartingDialog
import com.playlists.app.ui.PlaylistAccentColors
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.components.PlaylistActionsMenu
import com.playlists.app.ui.components.PlaylistColorDialog
import com.playlists.app.ui.components.TextInputDialog
import com.playlists.app.ui.reorder.DraggableItem
import com.playlists.app.ui.reorder.ReorderDragState
import com.playlists.app.ui.reorder.syncDisplayedKeys
import com.playlists.app.util.AppPrefs
import com.playlists.app.util.PlaylistExportShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    onOpenPlaylist: (Long) -> Unit,
    onQuickstart: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val songCounts by viewModel.playlistSongCounts.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val displayedKeys = remember { mutableStateListOf<String>() }
    val dragState = remember { ReorderDragState() }
    var showNewDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var colorTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var duplicateTarget by remember { mutableStateOf<Playlist?>(null) }
    var exportingPlaylistId by remember { mutableStateOf<Long?>(null) }
    var pushingPlaylistId by remember { mutableStateOf<Long?>(null) }
    var pushToServerJob by remember { mutableStateOf<Job?>(null) }
    val stableRedirectReady = AppPrefs.isStableRedirectReady(context)

    fun exportPlaylistPdf(playlistId: Long, songCount: Int) {
        if (exportingPlaylistId != null || songCount == 0) return
        exportingPlaylistId = playlistId
        scope.launch {
            val result = runCatching { viewModel.exportPlaylistPdf(playlistId) }
            exportingPlaylistId = null
            val export = result.getOrNull()
            when {
                export == null -> {
                    Toast.makeText(
                        context,
                        result.exceptionOrNull()?.message ?: context.getString(R.string.export_playlist_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                else -> {
                    PlaylistExportShare.sharePdf(context, export.file)
                    if (export.skippedMissing > 0) {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.export_playlist_skipped_missing,
                                export.bodyPages,
                                export.skippedMissing,
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    fun pushPlaylistToServer(playlistId: Long, playlistName: String, songCount: Int) {
        if (
            pushingPlaylistId != null ||
            exportingPlaylistId != null ||
            songCount == 0 ||
            !stableRedirectReady
        ) {
            return
        }
        pushingPlaylistId = playlistId
        pushToServerJob = scope.launch {
            val entries = viewModel.getPlaylistSongs(playlistId)
            val result = withContext(Dispatchers.IO) {
                PlayRemoteController.pushPlaylistPdfToServer(
                    context = context,
                    playlistId = playlistId,
                    playlistName = playlistName,
                    entries = entries,
                )
            }
            pushingPlaylistId = null
            pushToServerJob = null
            result.onFailure { error ->
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.push_playlist_to_server_failed,
                        error.message ?: context.getString(R.string.export_playlist_failed),
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun cancelPushToServer() {
        pushToServerJob?.cancel()
        pushToServerJob = null
        pushingPlaylistId = null
    }

    LaunchedEffect(playlists, dragState.draggingKey) {
        syncDisplayedKeys(displayedKeys, dragState.draggingKey, playlists.map { "p:${it.id}" })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.OutlinedButton(onClick = { showNewDialog = true }) {
                Text(stringResource(R.string.new_playlist))
            }
            androidx.compose.material3.OutlinedButton(onClick = onQuickstart) {
                Text(stringResource(R.string.quickstart))
            }
        }

        if (playlists.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.empty_playlists), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                state = listState,
                userScrollEnabled = !dragState.isDragging,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(displayedKeys.toList(), key = { it }) { key ->
                    val playlistId = key.removePrefix("p:").toLongOrNull() ?: return@items
                    val playlist = playlists.find { it.id == playlistId } ?: return@items
                    val paletteIndex = playlists.indexOfFirst { it.id == playlistId }.coerceAtLeast(0)
                    val songCount = songCounts[playlist.id] ?: 0
                    DraggableItem(
                        isDragging = dragState.draggingKey == key,
                        dragOffset = dragState.currentDragOffset(listState),
                        onTap = { onOpenPlaylist(playlist.id) },
                        onDragStart = { dragState.onDragStart(key, listState) },
                        onDrag = { delta -> dragState.onDrag(delta, listState, displayedKeys) },
                        onDragEnd = {
                            dragState.finishDrag {
                                val ids = displayedKeys.mapNotNull { it.removePrefix("p:").toLongOrNull() }
                                viewModel.reorderPlaylists(ids)
                            }
                        },
                        onDragCancel = { dragState.cancelDrag() },
                    ) {
                        PlaylistBlock(
                            playlist = playlist,
                            songCount = songCount,
                            fallbackColor = PlaylistAccentColors.palette[paletteIndex % PlaylistAccentColors.palette.size],
                            showPushToServer = stableRedirectReady,
                            pushingToServer = pushingPlaylistId == playlist.id,
                            busy = exportingPlaylistId != null || pushingPlaylistId != null,
                            onRename = { renameTarget = playlist },
                            onColor = { colorTarget = playlist },
                            onDelete = { deleteTarget = playlist },
                            onDuplicate = { duplicateTarget = playlist },
                            onExport = { exportPlaylistPdf(playlist.id, songCount) },
                            onPushToServer = {
                                pushPlaylistToServer(playlist.id, playlist.name, songCount)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        TextInputDialog(
            title = stringResource(R.string.new_playlist),
            initialValue = "",
            confirmLabel = stringResource(R.string.create),
            onDismiss = { showNewDialog = false },
            onConfirm = { name ->
                showNewDialog = false
                viewModel.createPlaylist(name) { id -> onOpenPlaylist(id) }
            },
        )
    }

    renameTarget?.let { playlist ->
        TextInputDialog(
            title = stringResource(R.string.rename_playlist),
            initialValue = playlist.name,
            confirmLabel = stringResource(R.string.save),
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                viewModel.renamePlaylist(playlist.id, name)
            },
        )
    }

    colorTarget?.let { playlist ->
        PlaylistColorDialog(
            currentColor = playlist.colorArgb,
            onDismiss = { colorTarget = null },
            onColorSelected = { color ->
                colorTarget = null
                viewModel.setPlaylistColor(playlist.id, color)
            },
        )
    }

    deleteTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_playlist)) },
            text = { Text(stringResource(R.string.delete_playlist_confirm, playlist.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    if (PlayRemoteController.startupPlaylistId() == playlist.id) {
                        scope.launch(Dispatchers.IO) { PlayRemoteController.stop() }
                    }
                    viewModel.deletePlaylist(playlist.id)
                }) {
                    Text(stringResource(R.string.delete_playlist))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    duplicateTarget?.let { playlist ->
        TextInputDialog(
            title = stringResource(R.string.duplicate),
            initialValue = stringResource(R.string.duplicate_default_name, playlist.name),
            confirmLabel = stringResource(R.string.create),
            onDismiss = { duplicateTarget = null },
            onConfirm = { name ->
                duplicateTarget = null
                viewModel.duplicatePlaylist(playlist.id, name) { newId ->
                    onOpenPlaylist(newId)
                }
            },
        )
    }

    if (exportingPlaylistId != null) {
        BasicAlertDialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(stringResource(R.string.export_playlist_working))
                }
            }
        }
    }

    if (pushingPlaylistId != null) {
        RemotePlayStartingDialog(
            mode = RemotePlayMode.STABLE,
            phase = RemotePlayStartPhase.UPLOADING_PDF,
            onCancel = { cancelPushToServer() },
        )
    }
}

@Composable
private fun PlaylistBlock(
    playlist: Playlist,
    songCount: Int,
    fallbackColor: Int,
    showPushToServer: Boolean,
    pushingToServer: Boolean,
    busy: Boolean,
    onRename: () -> Unit,
    onColor: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onPushToServer: () -> Unit,
) {
    val bg = Color(playlist.colorArgb ?: fallbackColor)
    val onBg = if (bg.luminance() > 0.5f) Color.Black else Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.playlist_with_song_count,
                    songCount,
                    playlist.name,
                    songCount,
                ),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = onBg,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PlaylistActionsMenu(
                iconTint = onBg,
                iconSize = 20.dp,
                exportEnabled = songCount > 0 && !busy,
                showPushToServer = showPushToServer,
                pushToServerEnabled = songCount > 0 && !busy && !pushingToServer,
                onRename = onRename,
                onColor = onColor,
                onDelete = onDelete,
                onDuplicate = onDuplicate,
                onExport = onExport,
                onPushToServer = onPushToServer,
            )
        }
    }
}
