package com.playlists.app.ui.screens

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.key
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playlists.app.R
import com.playlists.app.data.FileType
import com.playlists.app.find.SearchResult
import com.playlists.app.ui.ChartAssistantUiState
import com.playlists.app.ui.ChartAssistantViewModel
import com.playlists.app.ui.ChartAssistantViewModelFactory
import com.playlists.app.ui.components.SongMediaViewer
import com.playlists.app.util.AiCredentialStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartAssistantScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: ChartAssistantViewModel = viewModel(
        factory = ChartAssistantViewModelFactory(app, playlistId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startRecording()
    }

    LaunchedEffect(Unit) {
        viewModel.savedSongId.collect { songId ->
            onSaved(songId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chart_assistant_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            is ChartAssistantUiState.Preview -> PreviewContent(
                modifier = Modifier.padding(padding),
                state = current,
                onNudgeKey = viewModel::nudgePreviewKey,
                onConfirm = viewModel::confirmSave,
                onCancel = viewModel::cancelPreview,
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!AiCredentialStore.hasOpenAiApiKey(context)) {
                    Text(
                        text = stringResource(R.string.chart_assistant_no_api_key),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                when (current) {
                    is ChartAssistantUiState.Idle -> IdleMic(
                        onPressMic = {
                            when {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED -> viewModel.startRecording()
                                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onReleaseMic = viewModel::stopRecordingAndProcess,
                    )
                    is ChartAssistantUiState.Recording -> RecordingMic(
                        onRelease = viewModel::stopRecordingAndProcess,
                        onCancel = viewModel::cancelRecording,
                    )
                    is ChartAssistantUiState.Processing -> ProcessingBlock()
                    is ChartAssistantUiState.IntentReady -> {
                        Text(stringResource(R.string.chart_assistant_heard, current.intent.transcript))
                        ProcessingBlock()
                    }
                    is ChartAssistantUiState.SearchResults -> SearchResultsBlock(
                        transcript = current.intent.transcript,
                        playlistName = current.playlist.name,
                        results = current.results,
                        onSelect = viewModel::selectSearchResult,
                    )
                    is ChartAssistantUiState.Error -> {
                        Text(current.message, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = viewModel::dismissError) {
                            Text(stringResource(R.string.chart_assistant_try_again))
                        }
                    }
                    is ChartAssistantUiState.Preview -> Unit
                }
            }
        }
    }
}

@Composable
private fun IdleMic(
    onPressMic: () -> Unit,
    onReleaseMic: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.chart_assistant_hold_mic))
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        onPressMic()
                        waitForUpOrCancellation()
                        onReleaseMic()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = stringResource(R.string.chart_assistant_mic),
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun RecordingMic(
    onRelease: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.chart_assistant_recording))
        CircularProgressIndicator()
        Button(onClick = onRelease) {
            Text(stringResource(R.string.chart_assistant_stop_send))
        }
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun ProcessingBlock() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.chart_assistant_working),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun SearchResultsBlock(
    transcript: String,
    playlistName: String,
    results: List<SearchResult>,
    onSelect: (SearchResult) -> Unit,
) {
    Text(stringResource(R.string.chart_assistant_heard, transcript))
    Text(stringResource(R.string.chart_assistant_playlist_target, playlistName))
    Text(stringResource(R.string.chart_assistant_pick_result))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(results, key = { it.url }) { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(result) },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(result.title, style = MaterialTheme.typography.titleSmall)
                    if (result.snippet.isNotEmpty()) {
                        Text(
                            result.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        result.url,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewContent(
    modifier: Modifier = Modifier,
    state: ChartAssistantUiState.Preview,
    onNudgeKey: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                stringResource(
                    R.string.chart_assistant_confirm_add,
                    state.draft.title,
                    state.playlist.name,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { onNudgeKey(-1) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Text(
                        text = "−",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Text(
                    text = state.draft.key ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .widthIn(min = 48.dp),
                )
                FilledTonalIconButton(
                    onClick = { onNudgeKey(1) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            state.transposeNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            key(state.previewRevision) {
                SongMediaViewer(
                    file = state.pdfFile,
                    fileType = FileType.PDF,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.chart_assistant_confirm))
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
