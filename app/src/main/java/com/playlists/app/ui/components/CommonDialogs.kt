package com.playlists.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.playlists.app.R
import com.playlists.app.ui.PlaylistAccentColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistColorDialog(
    currentColor: Int?,
    onDismiss: () -> Unit,
    onColorSelected: (Int?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_color_title)) },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlaylistAccentColors.palette.forEach { color ->
                    val selected = currentColor == color
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .then(
                                if (selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { onColorSelected(color) },
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (currentColor == null) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onColorSelected(null) },
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text("—", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = value.value,
                onValueChange = { value.value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = value.value.trim()
                if (trimmed.isNotEmpty()) onConfirm(trimmed)
            }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
fun AppUpdateBanner(
    state: com.playlists.app.ui.AppUpdateUiState,
    onDismiss: () -> Unit,
    onInstall: (java.io.File) -> Unit = {},
) {
    val message = when (state) {
        com.playlists.app.ui.AppUpdateUiState.Checking -> stringResource(R.string.update_app_checking)
        is com.playlists.app.ui.AppUpdateUiState.Downloading -> {
            val progress = state.progress
            if (progress != null) {
                stringResource(R.string.update_app_downloading_percent, (progress * 100).toInt())
            } else {
                stringResource(R.string.update_app_downloading)
            }
        }
        is com.playlists.app.ui.AppUpdateUiState.UpToDate -> stringResource(R.string.update_app_up_to_date, state.versionName)
        is com.playlists.app.ui.AppUpdateUiState.ReadyToInstall -> stringResource(R.string.update_app_ready, state.versionName)
        is com.playlists.app.ui.AppUpdateUiState.Failed -> stringResource(R.string.update_app_failed, state.message)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        when (state) {
            is com.playlists.app.ui.AppUpdateUiState.ReadyToInstall -> {
                TextButton(onClick = { onInstall(state.apk) }) {
                    Text(stringResource(R.string.update_app_install))
                }
            }
            is com.playlists.app.ui.AppUpdateUiState.UpToDate,
            is com.playlists.app.ui.AppUpdateUiState.Failed -> {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            }
            else -> Unit
        }
    }
}
