package com.playlists.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.playlists.app.R
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.components.TextInputDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickstartScreen(
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var matchedIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quickstart)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.quickstart_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                minLines = 6,
            )
            Button(
                onClick = {
                    scope.launch {
                        val (results, ids) = viewModel.matchQuickstartLines(input)
                        matchedIds = ids
                        summary = results.joinToString("\n") { result ->
                            val song = result.song
                            val target = if (song != null) {
                                com.playlists.app.ui.SongDisplay.titleWithKey(
                                    song.title,
                                    song.keySignature,
                                    song.isPlaceholder,
                                )
                            } else {
                                "(no match)"
                            }
                            "• ${result.line} → $target"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.match_songs))
            }
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }
            if (matchedIds.isNotEmpty()) {
                Button(
                    onClick = { showCreate = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.create))
                }
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = stringResource(R.string.new_playlist),
            initialValue = stringResource(R.string.quickstart_default_name),
            confirmLabel = stringResource(R.string.create),
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                viewModel.createPlaylist(name) { id ->
                    viewModel.applyQuickstart(id, matchedIds) {
                        onOpenPlaylist(id)
                    }
                }
            },
        )
    }
}
