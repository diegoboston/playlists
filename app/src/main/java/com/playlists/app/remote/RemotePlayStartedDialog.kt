package com.playlists.app.remote

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.playlists.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun RemotePlayDebugDialog(
    onDismiss: () -> Unit,
) {
    var debug by remember { mutableStateOf<RemotePlayDebugInfo?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        val info = withContext(Dispatchers.IO) { PlayRemoteController.collectDebugInfo() }
        if (isActive) {
            debug = info
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_debug_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val url = debug?.publicUrl ?: PlayRemoteController.currentUrl()
                if (url != null) {
                    RemotePlayClickableUrl(url = url)
                    Spacer(Modifier.height(12.dp))
                }
                val info = debug
                if (info == null) {
                    if (url == null) {
                        Text(stringResource(R.string.remote_debug_unavailable))
                    }
                } else if (info.hasIssues()) {
                    RemotePlayDebugPanel(info = info, onRefresh = { refreshTick++ })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { refreshTick++ }) {
                Text(stringResource(R.string.remote_debug_refresh))
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
internal fun RemotePlayClickableUrl(
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Text(
        text = url,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
        modifier = modifier.clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
    )
}

@Composable
internal fun RemotePlayDebugPanel(
    info: RemotePlayDebugInfo,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val cloudflare = info.mode == RemotePlayMode.CLOUDFLARE
    Text(
        stringResource(R.string.remote_debug_heading),
        style = MaterialTheme.typography.titleSmall,
    )
    info.warnings
        .filter { warning -> cloudflare || !warningLooksCloudflareSpecific(warning) }
        .forEach { warning ->
        Text(
            warning,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(8.dp))
    ProbeLine(stringResource(R.string.remote_debug_local), info.localProbe)
    if (cloudflare) {
        info.tunnelProbe?.let { ProbeLine(stringResource(R.string.remote_debug_tunnel), it) }
        Text(
            stringResource(
                R.string.remote_debug_cloudflared,
                if (info.tunnelProcessAlive) {
                    stringResource(R.string.remote_debug_running)
                } else {
                    stringResource(
                        R.string.remote_debug_stopped,
                        info.tunnelExitCode?.toString() ?: "?",
                    )
                },
            ),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Text(
        stringResource(R.string.remote_debug_server, if (info.serverAlive) "up" else "down"),
        style = MaterialTheme.typography.bodySmall,
    )
    if (cloudflare && info.cloudflaredLog.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.remote_debug_log),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            info.cloudflaredLog,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
    TextButton(onClick = onRefresh, modifier = Modifier.padding(top = 4.dp)) {
        Text(stringResource(R.string.remote_debug_refresh))
    }
    TextButton(onClick = { RemotePlayErrors.copyToClipboard(context, info.formatForCopy()) }) {
        Text(stringResource(R.string.remote_debug_copy))
    }
}

@Composable
private fun ProbeLine(label: String, probe: RemotePlayHealth.ProbeResult) {
    val status = if (probe.ok) "OK" else "FAIL"
    Text(
        "$label: $status — ${probe.detail}",
        style = MaterialTheme.typography.bodySmall,
        color = if (probe.ok) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.error
        },
    )
}

private fun warningLooksCloudflareSpecific(warning: String): Boolean {
    val lower = warning.lowercase()
    return lower.contains("cloudflared") ||
        lower.contains("tunnel not reachable") ||
        lower.contains("tunnel url")
}
