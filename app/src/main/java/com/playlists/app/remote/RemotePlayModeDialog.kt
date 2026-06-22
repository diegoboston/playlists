package com.playlists.app.remote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.playlists.app.R

@Composable
fun RemotePlayModeDialog(
    onDismiss: () -> Unit,
    onSelect: (RemotePlayMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_mode_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.remote_mode_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { onSelect(RemotePlayMode.CLOUDFLARE) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.remote_mode_cloudflare))
                }
                TextButton(
                    onClick = { onSelect(RemotePlayMode.LAN) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.remote_mode_lan))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
