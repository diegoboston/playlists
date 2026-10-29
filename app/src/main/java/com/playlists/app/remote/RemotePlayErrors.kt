package com.playlists.app.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.playlists.app.R

object RemotePlayErrors {
    fun format(error: Throwable): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = error
        while (current != null) {
            val label = if (current === error) "Error" else "Caused by"
            val text = current.message?.takeIf { it.isNotBlank() } ?: current.javaClass.simpleName
            parts.add("$label: $text")
            current = current.cause
        }
        val body = parts.joinToString("\n\n")
        val transportHint = transportFailureHint(body)
        return if (transportHint != null) {
            "$body\n\n$transportHint"
        } else if (looksLikeDnsFailure(body)) {
            "$body\n\nHint: Cloudflare could not resolve hostnames on this device. " +
                "Reconnect to Wi‑Fi or mobile data and try again, or use LAN only on the same Wi‑Fi."
        } else {
            body
        }
    }

    internal fun transportFailureHint(text: String): String? {
        val lower = text.lowercase()
        val lines = lower.lines()
        fun timedOut(line: String) = line.contains("timeout") || line.contains("timed out") ||
            line.contains("no recent network activity")
        val quicTimeout = lines.any { it.contains("quic") && timedOut(it) }
        val tcpTimeout = lines.any {
            (it.contains("dial tcp") && it.contains(":7844") || it.contains("protocol=http2")) && timedOut(it)
        }
        return when {
            quicTimeout && tcpTimeout -> "Both UDP and TCP tunnel connections timed out. This network may block Cloudflare tunnel traffic on port 7844. Try another network or LAN only on the same Wi-Fi."
            quicTimeout -> "The UDP tunnel connection timed out. UDP port 7844 may be blocked. Retry using TCP; it also needs port 7844."
            tcpTimeout -> "The TCP tunnel connection timed out. This network may block TCP port 7844. Try another network or LAN only on the same Wi-Fi."
            else -> null
        }
    }

    internal fun looksLikeDnsFailure(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("lookup") &&
            lower.contains(":53") &&
            (lower.contains("connection refused") || lower.contains("no such host"))
    }

    internal fun looksLikeTunnelHostnameUnresolved(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("unable to resolve host") ||
            lower.contains("no address associated with hostname")
    }

    fun tunnelReachabilityWarning(detail: String): String {
        val base = "Tunnel not reachable yet ($detail)."
        return if (Regex("HTTP 502\\b").containsMatchIn(detail)) {
            "$base Cloudflare returned 502: the connector may be unable to reach the phone’s local web server. Check the local server result below before restarting the tunnel."
        } else if (detail.contains("HTTP 530")) {
            "$base Cloudflare cannot route this tunnel to a healthy connector. A running " +
                "cloudflared process alone does not prove it is connected. Check the cloudflared " +
                "log below, then stop and restart remote play and use the newly shown session URL. " +
                "If it persists, try another network and check whether a VPN or firewall blocks the tunnel."
        } else if (looksLikeTunnelHostnameUnresolved(detail)) {
            "$base The tunnel hostname can take 30–60 seconds to appear in DNS after cloudflared " +
                "starts. Wait, then tap Refresh — checking too early can cache “not found” on " +
                "mobile networks. If it still fails, toggle airplane mode or use LAN on the same Wi‑Fi."
        } else {
            "$base Wait a few seconds, tap Refresh in the dialog, then open the URL."
        }
    }

    fun isTunnelUnreachableWarning(warning: String): Boolean =
        warning.lowercase().contains("tunnel not reachable")

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("remote play error", text))
    }
}

@Composable
fun RemotePlayErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    onRetryTcp: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_play)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                onRetryTcp?.let { retry ->
                    Text(stringResource(R.string.remote_retry_tcp_hint), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = retry) { Text(stringResource(R.string.remote_retry_tcp)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                RemotePlayErrors.copyToClipboard(context, message)
                onDismiss()
            }) {
                Text(stringResource(R.string.remote_error_copy))
            }
        },
    )
}
