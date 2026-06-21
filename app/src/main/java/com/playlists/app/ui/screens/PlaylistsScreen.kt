package com.playlists.app.ui.screens

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.data.Playlist
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.components.TextInputDialog
import com.playlists.app.ui.reorder.DraggableItem
import com.playlists.app.ui.reorder.handleLazyListDrag

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    onOpenPlaylist: (Long) -> Unit,
    onQuickstart: () -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val displayedKeys = remember { mutableStateListOf<String>() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var showNewDialog by remember { mutableStateOf(false) }

    LaunchedEffect(playlists, draggingKey) {
        if (draggingKey == null) {
            displayedKeys.clear()
            displayedKeys.addAll(playlists.map { "p:${it.id}" })
        }
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
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(displayedKeys.toList(), key = { it }) { key ->
                    val playlistId = key.removePrefix("p:").toLongOrNull() ?: return@items
                    val playlist = playlists.find { it.id == playlistId } ?: return@items
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
                            val ids = displayedKeys.mapNotNull { it.removePrefix("p:").toLongOrNull() }
                            viewModel.reorderPlaylists(ids)
                        },
                    ) {
                        PlaylistRow(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
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
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (playlist.colorArgb != null) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(playlist.colorArgb)),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
            }
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = if (playlist.colorArgb != null) Color(playlist.colorArgb) else Color.Unspecified,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
