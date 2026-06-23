package com.playlists.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.remote.PlayRemoteController
import com.playlists.app.remote.RemotePlayErrorDialog
import com.playlists.app.remote.RemotePlayErrors
import com.playlists.app.remote.RemotePlayMode
import com.playlists.app.remote.RemotePlayModeDialog
import com.playlists.app.remote.RemotePlayStartedDialog
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.util.AppPrefs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabsScreen(
    viewModel: PlaylistsViewModel,
    onOpenSong: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onQuickstart: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val remoteRunning by PlayRemoteController.running.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var showRemoteModeDialog by remember { mutableStateOf(false) }
    var pendingRemotePlaylistId by remember { mutableStateOf<Long?>(null) }
    var remoteStartedUrl by remember { mutableStateOf<String?>(null) }
    var remoteStartedMode by remember { mutableStateOf<RemotePlayMode?>(null) }

    val activePlaylistId = if (remoteRunning) PlayRemoteController.activePlaylistId else null
    val entries by viewModel.observePlaylistSongs(activePlaylistId ?: 0L)
        .collectAsStateWithLifecycle()

    LaunchedEffect(entries, remoteRunning, activePlaylistId) {
        if (!remoteRunning || activePlaylistId == null) return@LaunchedEffect
        if (PlayRemoteController.isRunningFor(activePlaylistId)) {
            PlayRemoteController.refreshSongs(entries)
        }
    }

    fun startRemote(playlistId: Long?, mode: RemotePlayMode) {
        scope.launch {
            val playlist = playlistId?.let { viewModel.getPlaylist(it) }
            if (playlistId != null && playlist == null) {
                Toast.makeText(context, R.string.remote_playlist_gone, Toast.LENGTH_LONG).show()
                return@launch
            }
            val list = if (playlistId != null) viewModel.getPlaylistSongs(playlistId) else emptyList()
            val name = playlist?.name ?: context.getString(R.string.app_name)
            PlayRemoteController.start(context, playlistId, name, list, mode)
                .onSuccess { url ->
                    remoteStartedUrl = url
                    remoteStartedMode = mode
                }
                .onFailure { error ->
                    remoteError = RemotePlayErrors.format(error)
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.app_name))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (remoteRunning) {
                                        PlayRemoteController.stop()
                                        Toast.makeText(
                                            context,
                                            R.string.remote_stopped,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@IconButton
                                    }
                                    pendingRemotePlaylistId = AppPrefs.getLastPlaylistId(context)
                                    showRemoteModeDialog = true
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = stringResource(R.string.remote_play),
                                    tint = if (remoteRunning) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                                )
                            }
                            IconButton(onClick = onSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.settings),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_songs)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_playlists)) },
                )
            }
            when (selectedTab) {
                0 -> SongsScreen(viewModel, onOpenSong)
                1 -> PlaylistsScreen(viewModel, onOpenPlaylist, onQuickstart)
            }
        }
    }

    remoteError?.let { message ->
        RemotePlayErrorDialog(message = message, onDismiss = { remoteError = null })
    }

    if (showRemoteModeDialog) {
        RemotePlayModeDialog(
            onDismiss = {
                showRemoteModeDialog = false
                pendingRemotePlaylistId = null
            },
            onSelect = { mode ->
                showRemoteModeDialog = false
                pendingRemotePlaylistId?.let { startRemote(it, mode) }
                pendingRemotePlaylistId = null
            },
        )
    }

    remoteStartedUrl?.let { url ->
        RemotePlayStartedDialog(
            url = url,
            mode = remoteStartedMode ?: RemotePlayMode.CLOUDFLARE,
            onDismiss = {
                remoteStartedUrl = null
                remoteStartedMode = null
            },
        )
    }
}
