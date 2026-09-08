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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import com.playlists.app.ui.PdfHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ChartKeyPreviewContent(
    title: String,
    chartKeyLabel: String?,
    playKeyLabel: String?,
    lyricsOnly: Boolean = false,
    chartKeyGuessed: Boolean,
    transposeNote: String?,
    previewRevision: Int,
    pdfFile: File,
    bodyTextSize: Float?,
    confirmLabel: String,
    onNudgeKey: (Int) -> Unit,
    onNudgeFontSize: (Int) -> Unit,
    onSelectChartKey: (String) -> Unit,
    spellingPreference: AccidentalSpelling = AccidentalSpelling.Auto,
    onPreferFlats: () -> Unit = {},
    onPreferSharps: () -> Unit = {},
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onTryNext: (() -> Unit)? = null,
    tryNextEnabled: Boolean = false,
    tryNextLabel: String? = null,
    cancelLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    var showKeyPicker by remember { mutableStateOf(false) }
    var pageCount by remember { mutableIntStateOf(1) }
    var pageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(pdfFile, previewRevision) {
        pageIndex = 0
        pageCount = withContext(Dispatchers.IO) {
            PdfHelper.invalidate(pdfFile)
            PdfHelper.pageCount(pdfFile).coerceAtLeast(1)
        }
    }

    LaunchedEffect(pageCount) {
        pageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!lyricsOnly) Row(
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
            if (!lyricsOnly && chartKeyGuessed) {
                Text(
                    text = stringResource(R.string.chart_key_guessed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (!lyricsOnly) Row(
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chart_font_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 8.dp),
                )
                FontPillButton(
                    label = "−",
                    onClick = { onNudgeFontSize(-1) },
                )
                Text(
                    text = bodyTextSize?.toInt()?.toString() ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                FontPillButton(
                    label = "+",
                    onClick = { onNudgeFontSize(1) },
                )
            }
            if (!lyricsOnly) transposeNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            key(previewRevision to pageIndex) {
                PlaybackSongMedia(
                    file = pdfFile,
                    fileType = FileType.PDF,
                    pageIndex = pageIndex,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { if (pageIndex > 0) pageIndex-- },
                        enabled = pageIndex > 0,
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.chart_preview_prev_page),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(
                        onClick = { if (pageIndex < pageCount - 1) pageIndex++ },
                        enabled = pageIndex < pageCount - 1,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.chart_preview_next_page),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.playback_page_indicator,
                        pageIndex + 1,
                        pageCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
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
            if (onTryNext != null) {
                FilledTonalButton(
                    onClick = onTryNext,
                    enabled = tryNextEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tryNextLabel ?: stringResource(R.string.chart_assistant_try_next))
                }
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(cancelLabel ?: stringResource(R.string.cancel))
            }
        }
    }

    if (!lyricsOnly && showKeyPicker) {
        ChartKeyPickerSheet(
            selectedKey = chartKeyLabel,
            onDismiss = { showKeyPicker = false },
            onSelectKey = onSelectChartKey,
        )
    }
}

@Composable
private fun FontPillButton(
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(width = 56.dp, height = 40.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
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
