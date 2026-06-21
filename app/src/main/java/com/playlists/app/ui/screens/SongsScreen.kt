package com.playlists.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.data.Song
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.SongDisplay
import com.playlists.app.ui.reorder.DraggableItem
import com.playlists.app.ui.reorder.handleLazyListDrag

@Composable
fun SongsScreen(
    viewModel: PlaylistsViewModel,
    onOpenSong: (Long) -> Unit,
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val displayedKeys = remember { mutableStateListOf<String>() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(songs, draggingKey) {
        if (draggingKey == null) {
            displayedKeys.clear()
            displayedKeys.addAll(songs.map { "s:${it.id}" })
        }
    }

    if (songs.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.empty_songs), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = displayedKeys.toList(),
            key = { it },
        ) { key ->
            val songId = key.removePrefix("s:").toLongOrNull() ?: return@items
            val song = songs.find { it.id == songId } ?: return@items
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
                    val ids = displayedKeys.mapNotNull { it.removePrefix("s:").toLongOrNull() }
                    viewModel.reorderSongs(ids)
                },
            ) {
                SongRow(
                    song = song,
                    pageCount = viewModel.pageCount(song),
                    onClick = { onOpenSong(song.id) },
                    onDelete = { deleteTarget = song },
                )
            }
        }
    }

    deleteTarget?.let { song ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_song)) },
            text = { Text(stringResource(R.string.delete_song_confirm, song.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSong(song.id)
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.delete_song))
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
private fun SongRow(
    song: Song,
    pageCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (song.deletedAt != null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = SongDisplay.subtitle(song, pageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = SongDisplay.typeBadge(song, pageCount),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 4.dp),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_song))
            }
        }
    }
}
