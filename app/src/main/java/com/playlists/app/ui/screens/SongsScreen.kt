package com.playlists.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.data.Song
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.SongDeletePrompt
import com.playlists.app.ui.SongSortCriterion
import com.playlists.app.ui.SongDisplay
import com.playlists.app.ui.SongTitleWithKey
import com.playlists.app.ui.components.EditSongDialog
import com.playlists.app.util.ChartDraftStore
import kotlinx.coroutines.launch

@Composable
fun SongsScreen(
    viewModel: PlaylistsViewModel,
    onOpenSong: (Long) -> Unit,
    onNewKey: (Long) -> Unit,
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val sortState by viewModel.songSortState.collectAsStateWithLifecycle()
    val sortGeneration by viewModel.songSortGeneration.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var editTarget by remember { mutableStateOf<Song?>(null) }
    var deleteTarget by remember { mutableStateOf<SongDeletePrompt?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val displayedSongs = remember(songs, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            songs
        } else {
            songs.filter { song ->
                song.title.lowercase().contains(query) ||
                    song.keySignature.lowercase().contains(query) ||
                    song.notes.lowercase().contains(query)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.refreshSongListSort()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(sortGeneration) {
        if (sortGeneration > 0) {
            listState.scrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (songs.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_songs)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.sort_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SongSortButton(
                    label = stringResource(R.string.sort_songs_alpha),
                    selected = sortState.criterion == SongSortCriterion.Alpha,
                    onClick = { viewModel.sortSongs(SongSortCriterion.Alpha) },
                )
                SongSortButton(
                    label = stringResource(R.string.sort_songs_recently_added),
                    selected = sortState.criterion == SongSortCriterion.Added,
                    onClick = { viewModel.sortSongs(SongSortCriterion.Added) },
                )
                SongSortButton(
                    label = stringResource(R.string.sort_songs_recently_viewed),
                    selected = sortState.criterion == SongSortCriterion.Viewed,
                    onClick = { viewModel.sortSongs(SongSortCriterion.Viewed) },
                )
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
        } else if (displayedSongs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.no_songs_match_search), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(displayedSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onTap = { onOpenSong(song.id) },
                        onEdit = { editTarget = song },
                    )
                }
            }
        }
    }

    editTarget?.let { song ->
        EditSongDialog(
            song = song,
            hasChartSource = ChartDraftStore.hasChart(song.filePath),
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
            onNewKey = { onNewKey(song.id) },
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
private fun SongSortButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            ButtonDefaults.outlinedButtonBorder(enabled = true)
        },
        colors = if (selected) {
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SongRow(
    song: Song,
    onTap: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
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
