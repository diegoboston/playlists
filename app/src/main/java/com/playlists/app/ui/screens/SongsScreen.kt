package com.playlists.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.data.Song
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.SongDeletePrompt
import kotlinx.coroutines.launch
import com.playlists.app.ui.SongDisplay
import com.playlists.app.ui.SongTitleWithKey
import com.playlists.app.ui.reorder.DraggableItem
import com.playlists.app.ui.reorder.ReorderDragState
import com.playlists.app.ui.reorder.syncDisplayedKeys

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongsScreen(
    viewModel: PlaylistsViewModel,
    onOpenSong: (Long) -> Unit,
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val displayedKeys = remember { mutableStateListOf<String>() }
    val dragState = remember { ReorderDragState() }
    var editTarget by remember { mutableStateOf<Song?>(null) }
    var deleteTarget by remember { mutableStateOf<SongDeletePrompt?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(songs, dragState.draggingKey) {
        syncDisplayedKeys(displayedKeys, dragState.draggingKey, songs.map { "s:${it.id}" })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (songs.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { viewModel.sortSongsAlpha() }) {
                    Text(stringResource(R.string.sort_songs_alpha))
                }
                OutlinedButton(onClick = { viewModel.sortSongsByRecentlyAdded() }) {
                    Text(stringResource(R.string.sort_songs_recently_added))
                }
                OutlinedButton(onClick = { viewModel.sortSongsByRecentlyViewed() }) {
                    Text(stringResource(R.string.sort_songs_recently_viewed))
                }
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
        } else {
            LazyColumn(
                state = listState,
                userScrollEnabled = !dragState.isDragging,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = displayedKeys.toList(),
                    key = { it },
                ) { key ->
                    val songId = key.removePrefix("s:").toLongOrNull() ?: return@items
                    val song = songs.find { it.id == songId } ?: return@items
                    DraggableItem(
                        isDragging = dragState.draggingKey == key,
                        dragOffset = dragState.currentDragOffset(listState),
                        onTap = { onOpenSong(song.id) },
                        onDragStart = { dragState.onDragStart(key, listState) },
                        onDrag = { delta -> dragState.onDrag(delta, listState, displayedKeys) },
                        onDragEnd = {
                            dragState.finishDrag {
                                val ids = displayedKeys.mapNotNull { it.removePrefix("s:").toLongOrNull() }
                                viewModel.reorderSongs(ids)
                            }
                        },
                        onDragCancel = { dragState.cancelDrag() },
                    ) {
                        SongRow(
                            song = song,
                            onEdit = { editTarget = song },
                        )
                    }
                }
            }
        }
    }

    editTarget?.let { song ->
        EditSongDialog(
            song = song,
            onDismiss = { editTarget = null },
            onSave = { title, key, notes ->
                viewModel.updateSong(song.id, title, key, notes)
                editTarget = null
            },
            onDelete = {
                editTarget = null
                scope.launch {
                    deleteTarget = viewModel.prepareSongDelete(song.id)
                }
            },
        )
    }

    deleteTarget?.let { prompt ->
        val song = prompt.song
        val playlistList = prompt.playlistNames.joinToString(", ")
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_song)) },
            text = {
                Text(
                    if (prompt.playlistNames.isEmpty()) {
                        stringResource(R.string.delete_song_confirm, song.title)
                    } else {
                        stringResource(
                            R.string.delete_song_confirm_in_playlists,
                            song.title,
                            playlistList,
                        )
                    },
                )
            },
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
private fun EditSongDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (title: String, key: String, notes: String) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember(song.id) { mutableStateOf(song.title) }
    var key by remember(song.id) { mutableStateOf(song.keySignature) }
    var notes by remember(song.id) { mutableStateOf(song.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_song)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.key_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(18.dp),
                        )
                        Text(stringResource(R.string.delete_song))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, key, notes) },
                enabled = title.trim().isNotEmpty(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun SongRow(
    song: Song,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SongTitleWithKey(
                    title = song.title,
                    keySignature = song.keySignature,
                    isPlaceholder = song.isPlaceholder,
                )
                val noteLine = SongDisplay.notesLine(song.notes)
                if (noteLine.isNotEmpty()) {
                    Text(
                        text = noteLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_song),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
