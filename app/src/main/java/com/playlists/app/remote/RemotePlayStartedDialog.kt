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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.playlists.app.R

@Composable
internal fun RemotePlayDebugPanel(
    info: RemotePlayDebugInfo,
    onRefresh: () -> Unit,
    onRetryTcp: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val cloudflare = info.mode == RemotePlayMode.CLOUDFLARE || info.mode == RemotePlayMode.STABLE
    Text(
        stringResource(R.string.remote_debug_heading),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        stringResource(R.string.remote_debug_probe_hint),
        style = MaterialTheme.typography.bodySmall,
    )
    Text(info.connectionSummary(), style = MaterialTheme.typography.bodyMedium)
    if (cloudflare) {
        val protocol = info.lastRegisteredProtocol
        Text(
            "Last registered transport: " + when (protocol) {
                "quic" -> "QUIC (UDP)"
                "http2" -> "HTTP/2 (TCP)"
                else -> "No registered connection observed"
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
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
    info.urlProbes.forEach { probe ->
        ProbeLine(probe.label, probe.result, showUrl = true)
    }
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
            color = checkColor(info.tunnelProcessAlive),
        )
    }
    Text(
        stringResource(R.string.remote_debug_server, if (info.serverAlive) "up" else "down"),
        style = MaterialTheme.typography.bodySmall,
        color = checkColor(info.serverAlive),
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
    if (cloudflare && info.hasCloudflareIssues() && info.localProbe.ok &&
        info.tunnelProbe?.detail?.startsWith("HTTP 502") != true && onRetryTcp != null) {
        Text(stringResource(R.string.remote_retry_tcp_hint), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetryTcp) { Text(stringResource(R.string.remote_retry_tcp)) }
    }
    TextButton(onClick = onRefresh, modifier = Modifier.padding(top = 4.dp)) {
        Text(stringResource(R.string.remote_debug_refresh))
    }
    TextButton(onClick = { RemotePlayErrors.copyToClipboard(context, info.formatForCopy()) }) {
        Text(stringResource(R.string.remote_debug_copy))
    }
}

@Composable
private fun ProbeLine(label: String, probe: RemotePlayHealth.ProbeResult, showUrl: Boolean = false) {
    val status = if (probe.ok) "OK" else "FAIL"
    Text(
        "$label: $status — ${probe.detail}" + if (showUrl) "\n${probe.label}" else "",
        style = MaterialTheme.typography.bodySmall,
        color = checkColor(probe.ok),
    )
}

private fun warningLooksCloudflareSpecific(warning: String): Boolean = warning.looksCloudflareSpecific()

private fun checkColor(ok: Boolean): Color =
    if (ok) Color(0xFF2E7D32) else Color(0xFFC62828)
