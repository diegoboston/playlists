package com.playlists.app.remote

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.playlists.app.R

@Composable
internal fun RemotePlayDebugPanel(
    info: RemotePlayDebugInfo,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val cloudflare = info.mode == RemotePlayMode.CLOUDFLARE || info.mode == RemotePlayMode.STABLE
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
    if (cloudflare && info.hasCloudflareIssues() && info.cloudflaredLog.isNotBlank()) {
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

private fun warningLooksCloudflareSpecific(warning: String): Boolean = warning.looksCloudflareSpecific()
