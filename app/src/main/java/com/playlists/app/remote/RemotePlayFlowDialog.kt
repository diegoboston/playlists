package com.playlists.app.remote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.playlists.app.R
import com.playlists.app.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun RemotePlayFlowDialog(
    state: RemotePlayFlowState,
    onCancel: () -> Unit,
    onCloseStarted: () -> Unit,
    onStopRemote: () -> Unit,
    onSelectMode: (RemotePlayMode) -> Unit,
) {
    when (state) {
        RemotePlayFlowState.ChooseMode -> {
            val context = LocalContext.current
            val stableReady = remember { AppPrefs.isStableRedirectReady(context) }
            AlertDialog(
                onDismissRequest = onCancel,
                title = { Text(stringResource(R.string.remote_mode_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.remote_mode_message),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (stableReady) {
                            TextButton(
                                onClick = { onSelectMode(RemotePlayMode.STABLE) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.remote_mode_stable))
                            }
                        }
                        TextButton(
                            onClick = { onSelectMode(RemotePlayMode.CLOUDFLARE) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.remote_mode_cloudflare))
                        }
                        TextButton(
                            onClick = { onSelectMode(RemotePlayMode.LAN) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.remote_mode_lan))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
        is RemotePlayFlowState.Starting -> {
            AlertDialog(
                onDismissRequest = onCancel,
                title = { Text(stringResource(R.string.remote_play)) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(
                                when (state.mode) {
                                    RemotePlayMode.STABLE -> R.string.remote_starting_stable
                                    RemotePlayMode.CLOUDFLARE -> R.string.remote_starting_cloudflare
                                    RemotePlayMode.LAN -> R.string.remote_starting_lan
                                },
                            ),
                            modifier = Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
        is RemotePlayFlowState.Started -> {
            RemotePlayStartedDialog(
                mode = state.mode,
                onDismiss = onCloseStarted,
                onStop = onStopRemote,
            )
        }
    }
}

@Composable
fun RemotePlayStartedDialog(
    mode: RemotePlayMode,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    titleRes: Int = R.string.remote_started,
) {
    val context = LocalContext.current
    var debug by remember { mutableStateOf<RemotePlayDebugInfo?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val pin = remember(mode) {
        if (mode != RemotePlayMode.LAN) AppPrefs.getRemotePin(context) else null
    }

    LaunchedEffect(mode, refreshTick) {
        if (mode == RemotePlayMode.LAN) return@LaunchedEffect
        if (!PlayRemoteController.running.value) {
            debug = null
            return@LaunchedEffect
        }
        val info = withContext(Dispatchers.IO) { PlayRemoteController.collectDebugInfo() }
        if (!isActive || !PlayRemoteController.running.value) return@LaunchedEffect
        debug = info
        if (info?.hasIssues() != false) {
            delay(15_000)
            if (isActive && PlayRemoteController.running.value) {
                refreshTick++
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            RemotePlayStartedDialogContent(
                mode = mode,
                pin = pin,
                debug = debug,
                onRefreshDebug = { refreshTick++ },
            )
        },
        confirmButton = {
            TextButton(onClick = onStop) {
                Text(stringResource(R.string.remote_stop_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
internal fun RemotePlayStartedDialogContent(
    mode: RemotePlayMode,
    pin: String?,
    debug: RemotePlayDebugInfo?,
    onRefreshDebug: () -> Unit,
) {
    val context = LocalContext.current
    val playlistId = PlayRemoteController.activePlaylistId
    val urlEntries = RemotePlayUrls.collect(context, playlistId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(
                when (mode) {
                    RemotePlayMode.STABLE -> R.string.remote_started_message_stable
                    RemotePlayMode.CLOUDFLARE -> R.string.remote_started_message_cloudflare
                    RemotePlayMode.LAN -> R.string.remote_started_message_lan
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (mode != RemotePlayMode.LAN && pin != null) {
            Text(
                stringResource(R.string.remote_started_pin, pin),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        if (urlEntries.isEmpty()) {
            Text(
                stringResource(R.string.remote_debug_unavailable),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                stringResource(R.string.remote_urls_heading),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            RemotePlayUrlList(
                entries = urlEntries,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (mode != RemotePlayMode.LAN) {
            debug?.let { info ->
                Spacer(Modifier.height(16.dp))
                RemotePlayDebugPanel(
                    info = info,
                    onRefresh = onRefreshDebug,
                )
            }
        }
    }
}
