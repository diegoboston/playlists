package com.playlists.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.playlists.app.R

@Composable
fun AppUpdateInProgressBanner(
    state: AppUpdateUiState?,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AppUpdateUiState.Checking -> AppUpdateDownloadProgress(
            progress = null,
            indeterminateLabel = R.string.update_app_checking,
            modifier = modifier,
        )
        is AppUpdateUiState.Downloading -> AppUpdateDownloadProgress(
            progress = state.progress,
            modifier = modifier,
        )
        else -> Unit
    }
}

@Composable
fun AppUpdateDownloadProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    indeterminateLabel: Int = R.string.update_app_downloading,
) {
    val percent = progress?.times(100f)?.toInt()?.coerceIn(0, 100)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (percent != null) {
                stringResource(R.string.update_app_downloading_percent, percent)
            } else {
                stringResource(indeterminateLabel)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        if (percent != null) {
            LinearProgressIndicator(
                progress = { progress!!.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
