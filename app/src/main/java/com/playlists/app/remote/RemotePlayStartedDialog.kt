package com.playlists.app.remote

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.playlists.app.R

@Composable
fun RemotePlayStartedDialog(
    url: String,
    mode: RemotePlayMode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val openBrowser = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_started)) },
        text = {
            Column {
                Text(
                    stringResource(
                        when (mode) {
                            RemotePlayMode.CLOUDFLARE -> R.string.remote_started_message_cloudflare
                            RemotePlayMode.LAN -> R.string.remote_started_message_lan
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable(onClick = openBrowser),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = openBrowser) {
                Text(stringResource(R.string.remote_open_in_browser))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}
