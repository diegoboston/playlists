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
import androidx.compose.material3.OutlinedButton
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
import com.playlists.app.util.QuickstartMatcher
import kotlinx.coroutines.launch

private enum class QuickstartCreateMode {
    MatchedOnly,
    WithPlaceholders,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickstartScreen(
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var matchResults by remember { mutableStateOf<List<QuickstartMatcher.MatchResult>>(emptyList()) }
    var createMode by remember { mutableStateOf<QuickstartCreateMode?>(null) }
    val scope = rememberCoroutineScope()
    val noMatchLabel = stringResource(R.string.quickstart_no_match)

    val matchedResults = remember(matchResults) { matchResults.filter { it.song != null } }
    val unmatchedResults = remember(matchResults) { matchResults.filter { it.song == null } }

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
                        val (results, _) = viewModel.matchQuickstartLines(input)
                        matchResults = results
                        summary = formatQuickstartSummary(results, noMatchLabel)
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
            if (matchedResults.isNotEmpty()) {
                Button(
                    onClick = { createMode = QuickstartCreateMode.MatchedOnly },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.create))
                }
            }
            if (unmatchedResults.isNotEmpty()) {
                OutlinedButton(
                    onClick = { createMode = QuickstartCreateMode.WithPlaceholders },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (matchedResults.isNotEmpty()) 8.dp else 12.dp),
                ) {
                    Text(stringResource(R.string.create_with_placeholders))
                }
            }
        }
    }

    if (createMode != null) {
        TextInputDialog(
            title = stringResource(R.string.new_playlist),
            initialValue = stringResource(R.string.quickstart_default_name),
            confirmLabel = stringResource(R.string.create),
            onDismiss = { createMode = null },
            onConfirm = { name ->
                val mode = createMode
                createMode = null
                viewModel.createPlaylist(name) { id ->
                    when (mode) {
                        QuickstartCreateMode.MatchedOnly -> {
                            val ids = QuickstartMatcher.matchedSongIds(matchResults)
                            viewModel.applyQuickstart(id, ids) {
                                onOpenPlaylist(id)
                            }
                        }
                        QuickstartCreateMode.WithPlaceholders -> {
                            viewModel.applyQuickstartWithPlaceholders(id, matchResults) {
                                onOpenPlaylist(id)
                            }
                        }
                        null -> Unit
                    }
                }
            },
        )
    }
}

private fun formatQuickstartSummary(
    results: List<QuickstartMatcher.MatchResult>,
    noMatchLabel: String,
): String {
    val (matched, unmatched) = results.partition { it.song != null }
    return (matched + unmatched).joinToString("\n") { result ->
        val song = result.song
        val target = if (song != null) {
            com.playlists.app.ui.SongDisplay.adjustedSongTitle(
                song.title,
                song.keySignature,
                song.isPlaceholder,
            )
        } else {
            noMatchLabel
        }
        "• ${result.line} → $target"
    }
}
