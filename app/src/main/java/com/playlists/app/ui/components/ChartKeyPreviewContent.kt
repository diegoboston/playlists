package com.playlists.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.playlists.app.R
import com.playlists.app.data.FileType
import com.playlists.app.render.AccidentalSpelling
import java.io.File

@Composable
fun ChartKeyPreviewContent(
    title: String,
    chartKeyLabel: String?,
    playKeyLabel: String?,
    chartKeyGuessed: Boolean,
    transposeNote: String?,
    previewRevision: Int,
    pdfFile: File,
    confirmLabel: String,
    onNudgeKey: (Int) -> Unit,
    onSelectChartKey: (String) -> Unit,
    spellingPreference: AccidentalSpelling = AccidentalSpelling.Auto,
    onPreferFlats: () -> Unit = {},
    onPreferSharps: () -> Unit = {},
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showKeyPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chart_key_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .clickable { showKeyPicker = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chartKeyLabel ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.chart_key_picker_title),
                    )
                }
            }
            if (chartKeyGuessed) {
                Text(
                    text = stringResource(R.string.chart_key_guessed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chart_key_play_in),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 8.dp),
                )
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
                    text = playKeyLabel ?: "—",
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
                SpellingToggleButton(
                    label = "♭",
                    selected = spellingPreference == AccidentalSpelling.Flats,
                    onClick = onPreferFlats,
                )
                SpellingToggleButton(
                    label = "♯",
                    selected = spellingPreference == AccidentalSpelling.Sharps,
                    onClick = onPreferSharps,
                )
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

    if (showKeyPicker) {
        ChartKeyPickerSheet(
            selectedKey = chartKeyLabel,
            onDismiss = { showKeyPicker = false },
            onSelectKey = onSelectChartKey,
        )
    }
}

@Composable
private fun SpellingToggleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = if (selected) {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        ButtonDefaults.filledTonalButtonColors()
    }
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .padding(start = 8.dp)
            .size(48.dp),
        colors = colors,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
