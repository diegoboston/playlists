package com.playlists.app.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playlists.app.R
import com.playlists.app.ui.ChartRetransposeUiState
import com.playlists.app.ui.ChartRetransposeViewModel
import com.playlists.app.ui.ChartRetransposeViewModelFactory
import com.playlists.app.ui.components.ChartKeyPreviewContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartRetransposeScreen(
    songId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: ChartRetransposeViewModel = viewModel(
        factory = ChartRetransposeViewModelFactory(app, songId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.song_new_key_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelPreview()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            is ChartRetransposeUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            is ChartRetransposeUiState.Preview -> ChartKeyPreviewContent(
                modifier = Modifier.padding(padding),
                title = stringResource(R.string.song_new_key_prompt, current.song.title),
                keyLabel = current.draft.key,
                transposeNote = current.transposeNote,
                previewRevision = current.previewRevision,
                pdfFile = current.pdfFile,
                confirmLabel = stringResource(R.string.save),
                onNudgeKey = viewModel::nudgePreviewKey,
                onConfirm = viewModel::confirmSave,
                onCancel = {
                    viewModel.cancelPreview()
                    onBack()
                },
            )
            is ChartRetransposeUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(current.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
