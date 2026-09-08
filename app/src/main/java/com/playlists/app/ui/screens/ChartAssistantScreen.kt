@file:OptIn(ExperimentalMaterial3Api::class)

package com.playlists.app.ui.screens

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playlists.app.R
import com.playlists.app.ai.ChartSearchMode
import com.playlists.app.ui.ChartAssistantUiState
import com.playlists.app.ui.ChartAssistantViewModel
import com.playlists.app.ui.ChartAssistantViewModelFactory
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.rememberOpenAiKeyReady
import com.playlists.app.render.AccidentalSpelling
import com.playlists.app.ui.components.ChartKeyPreviewContent

@Composable
fun ChartAssistantScreen(
    playlistId: Long?,
    playlistsViewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: ChartAssistantViewModel = viewModel(
        factory = ChartAssistantViewModelFactory(app, playlistId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingChartImport by playlistsViewModel.pendingChartImport.collectAsStateWithLifecycle()
    val openAiKeyReady = rememberOpenAiKeyReady()
    var searchMode by rememberSaveable { mutableStateOf(ChartSearchMode.ChordsAndLyrics.name) }
    val selectedMode = remember(searchMode) {
        runCatching { ChartSearchMode.valueOf(searchMode) }.getOrDefault(ChartSearchMode.ChordsAndLyrics)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* hold mic again after granting permission */ }

    LaunchedEffect(Unit) {
        viewModel.savedSongId.collect { songId ->
            onSaved(songId)
        }
    }

    LaunchedEffect(pendingChartImport) {
        val pending = pendingChartImport ?: return@LaunchedEffect
        if (pending.playlistId != playlistId) return@LaunchedEffect
        playlistsViewModel.consumePendingChartImport()
        viewModel.importSharedUrl(pending.url, pending.titleHint)
    }

    val handleBack = {
        if (!viewModel.handleBack()) {
            onBack()
        }
    }

    // Preview Back leaves Find chart; Cancel on the confirm panel does the same.
    BackHandler(onBack = handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chart_assistant_title)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
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
                onNudgeFontSize = viewModel::nudgeFontSize,
                onSelectChartKey = viewModel::setChartKey,
                onPreferFlats = { viewModel.setSpellingPreference(AccidentalSpelling.Flats) },
                onPreferSharps = { viewModel.setSpellingPreference(AccidentalSpelling.Sharps) },
                onConfirm = viewModel::confirmSave,
                onTryNext = viewModel::tryNextResult,
                onCancel = {
                    viewModel.cancelPreview()
                    onBack()
                },
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!openAiKeyReady) {
                    Text(
                        text = stringResource(R.string.chart_assistant_no_api_key),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                when (current) {
                    is ChartAssistantUiState.Idle,
                    is ChartAssistantUiState.Recording,
                    -> IdleSearchContent(
                        isRecording = current is ChartAssistantUiState.Recording,
                        searchMode = selectedMode,
                        onSearchModeChange = { searchMode = it.name },
                        onPressMic = {
                            when {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    viewModel.startRecording(selectedMode)
                                }
                                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onReleaseMic = viewModel::stopRecordingAndProcess,
                        onCancelMic = viewModel::cancelRecording,
                        onTypedSearch = { text ->
                            viewModel.searchFromText(text, selectedMode)
                        },
                    )
                    is ChartAssistantUiState.Processing -> ProcessingBlock()
                    is ChartAssistantUiState.IntentReady -> {
                        Text(stringResource(R.string.chart_assistant_heard, current.intent.transcript))
                        ProcessingBlock()
                    }
                    is ChartAssistantUiState.SearchResults -> ProcessingBlock()
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
private fun IdleSearchContent(
    isRecording: Boolean,
    searchMode: ChartSearchMode,
    onSearchModeChange: (ChartSearchMode) -> Unit,
    onPressMic: () -> Unit,
    onReleaseMic: () -> Unit,
    onCancelMic: () -> Unit,
    onTypedSearch: (String) -> Unit,
) {
    var typedQuery by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HoldToRecordMic(
            isRecording = isRecording,
            onPressMic = onPressMic,
            onReleaseMic = onReleaseMic,
            onCancelMic = onCancelMic,
        )
        if (!isRecording) {
            OutlinedTextField(
                value = typedQuery,
                onValueChange = { typedQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.chart_assistant_typed_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (typedQuery.isNotBlank()) onTypedSearch(typedQuery)
                    },
                ),
            )
            ChartSearchModeSelector(
                selected = searchMode,
                onSelected = onSearchModeChange,
            )
            OutlinedButton(
                onClick = { onTypedSearch(typedQuery) },
                enabled = typedQuery.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp),
                )
                Text(stringResource(R.string.chart_assistant_search))
            }
        }
    }
}

@Composable
private fun ChartSearchModeSelector(
    selected: ChartSearchMode,
    onSelected: (ChartSearchMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == ChartSearchMode.ChordsAndLyrics,
            onClick = { onSelected(ChartSearchMode.ChordsAndLyrics) },
            label = { Text(stringResource(R.string.chart_assistant_mode_chords_lyrics)) },
        )
        FilterChip(
            selected = selected == ChartSearchMode.LyricsOnly,
            onClick = { onSelected(ChartSearchMode.LyricsOnly) },
            label = { Text(stringResource(R.string.chart_assistant_mode_lyrics_only)) },
        )
    }
}

@Composable
private fun HoldToRecordMic(
    isRecording: Boolean,
    onPressMic: () -> Unit,
    onReleaseMic: () -> Unit,
    onCancelMic: () -> Unit,
) {
    val micBackground = if (isRecording) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val micTint = if (isRecording) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (isRecording) {
                stringResource(R.string.chart_assistant_recording)
            } else {
                stringResource(R.string.chart_assistant_hold_mic)
            },
        )
        if (isRecording) {
            RecordingWaveform(
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .fillMaxWidth(0.55f)
                    .height(36.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(micBackground, MaterialTheme.shapes.large)
                .pointerInput(onPressMic, onReleaseMic, onCancelMic) {
                    awaitEachGesture {
                        awaitFirstDown()
                        onPressMic()
                        when (waitForUpOrCancellation()) {
                            null -> onCancelMic()
                            else -> onReleaseMic()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = stringResource(R.string.chart_assistant_mic),
                modifier = Modifier.size(48.dp),
                tint = micTint,
            )
        }
    }
}

@Composable
private fun RecordingWaveform(
    modifier: Modifier = Modifier,
    color: Color,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording-wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    Canvas(modifier = modifier) {
        val barCount = 14
        val gap = size.width / (barCount * 3f)
        val barWidth = gap * 1.4f
        val centerY = size.height / 2f
        for (i in 0 until barCount) {
            val t = (i.toFloat() / barCount + phase) % 1f
            val wave = sin(t * 2 * PI).toFloat()
            val normalized = (wave + 1f) / 2f
            val barHeight = size.height * (0.15f + 0.85f * normalized)
            val x = i * (barWidth + gap) + gap
            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
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
private fun PreviewContent(
    modifier: Modifier = Modifier,
    state: ChartAssistantUiState.Preview,
    onNudgeKey: (Int) -> Unit,
    onNudgeFontSize: (Int) -> Unit,
    onSelectChartKey: (String) -> Unit,
    onPreferFlats: () -> Unit,
    onPreferSharps: () -> Unit,
    onConfirm: () -> Unit,
    onTryNext: () -> Unit,
    onCancel: () -> Unit,
) {
    ChartKeyPreviewContent(
        modifier = modifier,
        title = stringResource(R.string.chart_assistant_is_correct),
        chartKeyLabel = state.sourceDraft.chartKeyLabel(),
        playKeyLabel = state.draft.displayKeyLabel(),
        lyricsOnly = state.intent.searchMode == ChartSearchMode.LyricsOnly,
        chartKeyGuessed = state.chartKeyGuessed,
        transposeNote = state.transposeNote,
        previewRevision = state.previewRevision,
        pdfFile = state.pdfFile,
        bodyTextSize = state.bodyTextSize,
        confirmLabel = stringResource(R.string.chart_assistant_yes),
        onNudgeKey = onNudgeKey,
        onNudgeFontSize = onNudgeFontSize,
        onSelectChartKey = onSelectChartKey,
        spellingPreference = state.spellingPreference,
        onPreferFlats = onPreferFlats,
        onPreferSharps = onPreferSharps,
        onConfirm = onConfirm,
        onTryNext = onTryNext,
        tryNextEnabled = state.remainingResults.isNotEmpty(),
        onCancel = onCancel,
    )
}
