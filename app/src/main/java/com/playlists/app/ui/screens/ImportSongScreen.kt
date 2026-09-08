package com.playlists.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.util.CaptureImageContract
import com.playlists.app.util.CaptureImageStore
import com.playlists.app.util.CapturePageStart
import com.playlists.app.util.LocalFileImport
import com.playlists.app.util.PendingPageAdjust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSongScreen(
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by viewModel.pendingImport.collectAsStateWithLifecycle()
    val currentPending = pending
    if (currentPending == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var title by remember(currentPending.filePath) { mutableStateOf(currentPending.suggestedTitle) }
    var key by remember(currentPending.filePath) { mutableStateOf(currentPending.suggestedKey) }
    var notes by remember(currentPending.filePath) { mutableStateOf(currentPending.suggestedNotes) }
    var addingPage by remember { mutableStateOf(false) }
    val latestPending by rememberUpdatedState(currentPending)
    val canTakePhoto = remember(context) { CaptureImageStore.hasCameraApp(context) }

    val addPageLauncher = rememberLauncherForActivityResult(
        CaptureImageContract(),
    ) { success ->
        if (!success) return@rememberLauncherForActivityResult
        scope.launch {
            addingPage = true
            try {
                when (
                    val start = withContext(Dispatchers.IO) {
                        LocalFileImport.beginCapturedPage(
                            context,
                            CaptureImageStore.pendingFile(context),
                            latestPending,
                        )
                    }
                ) {
                    CapturePageStart.Failed -> {
                        Toast.makeText(context, R.string.import_file_failed, Toast.LENGTH_LONG).show()
                    }
                    is CapturePageStart.NeedsAdjust -> {
                        viewModel.setPendingPageAdjust(
                            PendingPageAdjust(start.stored.absolutePath, existingImport = latestPending),
                        )
                    }
                    is CapturePageStart.Ready -> {
                        val next = start.outcome.pending
                        if (next == null) {
                            Toast.makeText(context, R.string.import_file_failed, Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.setPendingImport(next)
                        }
                    }
                }
            } finally {
                addingPage = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_song)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearPendingImport()
                        onBack()
                    }) {
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
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.title_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(R.string.key_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.notes_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            if (currentPending.allowAddPages) {
                Text(
                    pluralStringResource(
                        R.plurals.import_page_count,
                        currentPending.pageCount,
                        currentPending.pageCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (canTakePhoto) {
                    OutlinedButton(
                        onClick = {
                            val launched = runCatching {
                                addPageLauncher.launch(CaptureImageStore.prepareNewCapture(context))
                            }.isSuccess
                            if (!launched) {
                                Toast.makeText(
                                    context,
                                    R.string.scan_image_camera_failed,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        enabled = !addingPage,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_add_page))
                    }
                }
            }
            Button(
                onClick = {
                    viewModel.saveImport(title, key, notes) { id ->
                        onSaved(id)
                    }
                },
                enabled = !addingPage,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (addingPage) {
        BasicAlertDialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(stringResource(R.string.scan_image_adding_page))
                }
            }
        }
    }
}
