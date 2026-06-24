package com.playlists.app.remote

import androidx.compose.foundation.layout.Arrangement
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
            AlertDialog(
                onDismissRequest = onCancel,
                title = { Text(stringResource(R.string.remote_mode_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.remote_mode_message),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = { onSelectMode(RemotePlayMode.CLOUDFLARE) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
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
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(
                                when (state.mode) {
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
                url = state.url,
                mode = state.mode,
                onDismiss = onCloseStarted,
                onStop = onStopRemote,
            )
        }
    }
}

@Composable
fun RemotePlayStartedDialog(
    url: String,
    mode: RemotePlayMode,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    var debug by remember { mutableStateOf<RemotePlayDebugInfo?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val pin = remember(mode) {
        if (mode == RemotePlayMode.CLOUDFLARE) AppPrefs.getRemotePin(context) else null
    }

    LaunchedEffect(mode, refreshTick) {
        if (mode != RemotePlayMode.CLOUDFLARE) return@LaunchedEffect
        val info = withContext(Dispatchers.IO) { PlayRemoteController.collectDebugInfo() }
        if (!isActive) return@LaunchedEffect
        debug = info
        if (info?.hasIssues() != false) {
            // Quick tunnels need DNS propagation; polling every few seconds can negative-cache NXDOMAIN.
            delay(15_000)
            refreshTick++
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_started)) },
        text = {
            RemotePlayStartedDialogContent(
                url = url,
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
    url: String,
    mode: RemotePlayMode,
    pin: String?,
    debug: RemotePlayDebugInfo?,
    onRefreshDebug: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(
                when (mode) {
                    RemotePlayMode.CLOUDFLARE -> R.string.remote_started_message_cloudflare
                    RemotePlayMode.LAN -> R.string.remote_started_message_lan
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (mode == RemotePlayMode.CLOUDFLARE && pin != null) {
            Text(
                stringResource(R.string.remote_started_pin, pin),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        RemotePlayUrlSection(
            url = url,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (mode == RemotePlayMode.CLOUDFLARE) {
            debug?.takeIf { it.hasIssues() }?.let { info ->
                Spacer(Modifier.height(16.dp))
                RemotePlayDebugPanel(
                    info = info,
                    onRefresh = onRefreshDebug,
                )
            }
        }
    }
}
