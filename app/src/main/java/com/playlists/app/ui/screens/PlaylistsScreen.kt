package com.playlists.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.data.Playlist
import com.playlists.app.remote.PlayRemoteController
import com.playlists.app.ui.PlaylistAccentColors
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.components.PlaylistColorDialog
import com.playlists.app.ui.components.TextInputDialog
import com.playlists.app.ui.reorder.DraggableItem
import com.playlists.app.ui.reorder.ReorderDragState
import com.playlists.app.ui.reorder.syncDisplayedKeys

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    onOpenPlaylist: (Long) -> Unit,
    onQuickstart: () -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val displayedKeys = remember { mutableStateListOf<String>() }
    val dragState = remember { ReorderDragState() }
    var showNewDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var colorTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

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
                    val playlistSongs by viewModel.observePlaylistSongs(playlist.id)
                        .collectAsStateWithLifecycle()
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
                            songCount = playlistSongs.size,
                            fallbackColor = PlaylistAccentColors.palette[paletteIndex % PlaylistAccentColors.palette.size],
                            onRename = { renameTarget = playlist },
                            onColor = { colorTarget = playlist },
                            onDelete = { deleteTarget = playlist },
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
                    if (PlayRemoteController.isRunningFor(playlist.id)) PlayRemoteController.stop()
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
}

@Composable
private fun PlaylistBlock(
    playlist: Playlist,
    songCount: Int,
    fallbackColor: Int,
    onRename: () -> Unit,
    onColor: () -> Unit,
    onDelete: () -> Unit,
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
            IconButton(onClick = onRename) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.rename_playlist),
                    tint = onBg,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onColor) {
                if (playlist.colorArgb != null) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f))
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(Color(playlist.colorArgb)),
                    )
                } else {
                    Icon(
                        Icons.Default.ColorLens,
                        contentDescription = stringResource(R.string.playlist_color),
                        tint = onBg,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_playlist),
                    tint = onBg,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
