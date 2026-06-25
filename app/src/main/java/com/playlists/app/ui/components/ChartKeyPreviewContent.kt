package com.playlists.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.playlists.app.R
import com.playlists.app.data.FileType
import java.io.File

@Composable
fun ChartKeyPreviewContent(
    title: String,
    keyLabel: String?,
    transposeNote: String?,
    previewRevision: Int,
    pdfFile: File,
    confirmLabel: String,
    onNudgeKey: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { onNudgeKey(-1) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Text(
                        text = "−",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Text(
                    text = keyLabel ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .widthIn(min = 48.dp),
                )
                FilledTonalIconButton(
                    onClick = { onNudgeKey(1) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            transposeNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            key(previewRevision) {
                SongMediaViewer(
                    file = pdfFile,
                    fileType = FileType.PDF,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text(confirmLabel)
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
