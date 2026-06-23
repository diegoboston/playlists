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
        return if (looksLikeDnsFailure(body)) {
            "$body\n\nHint: Cloudflare could not resolve hostnames on this device. " +
                "Install the latest app update (fixes cloudflared DNS), or use LAN only on the same Wi‑Fi."
        } else {
            body
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
        return if (looksLikeTunnelHostnameUnresolved(detail)) {
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
