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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.playlists.app.remote.RemotePlayDebugDialog
import com.playlists.app.remote.RemotePlayErrorDialog
import com.playlists.app.remote.RemotePlayErrors
import com.playlists.app.remote.RemotePlayFlowDialog
import com.playlists.app.remote.RemotePlayFlowState
import com.playlists.app.remote.RemotePlayMode
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.components.RemotePlayIconButton
import com.playlists.app.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var remoteFlow by remember { mutableStateOf<RemotePlayFlowState?>(null) }
    var pendingRemotePlaylistId by remember { mutableStateOf<Long?>(null) }
    var remoteStartGeneration by remember { mutableIntStateOf(0) }
    var showRemoteDebug by remember { mutableStateOf(false) }

    val activePlaylistId = if (remoteRunning) PlayRemoteController.activePlaylistId else null
    val entries by viewModel.observePlaylistSongs(activePlaylistId ?: 0L)
        .collectAsStateWithLifecycle()

    LaunchedEffect(entries, remoteRunning, activePlaylistId) {
        if (!remoteRunning || activePlaylistId == null) return@LaunchedEffect
        if (PlayRemoteController.isRunningFor(activePlaylistId)) {
            PlayRemoteController.refreshSongs(entries)
        }
    }

    fun cancelRemoteFlow() {
        remoteStartGeneration++
        remoteFlow = null
        pendingRemotePlaylistId = null
        scope.launch(Dispatchers.IO) { PlayRemoteController.stop() }
    }

    fun closeRemoteStartedDialog() {
        remoteFlow = null
    }

    fun stopRemoteFlow() {
        cancelRemoteFlow()
        Toast.makeText(context, R.string.remote_stopped, Toast.LENGTH_SHORT).show()
    }

    fun startRemote(playlistId: Long?, mode: RemotePlayMode) {
        val generation = remoteStartGeneration + 1
        remoteStartGeneration = generation
        remoteFlow = RemotePlayFlowState.Starting(mode)
        scope.launch {
            val playlist = playlistId?.let { viewModel.getPlaylist(it) }
            if (playlistId != null && playlist == null) {
                if (generation == remoteStartGeneration) {
                    remoteFlow = null
                    Toast.makeText(context, R.string.remote_playlist_gone, Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val list = if (playlistId != null) viewModel.getPlaylistSongs(playlistId) else emptyList()
            val name = playlist?.name ?: context.getString(R.string.app_name)
            val result = withContext(Dispatchers.IO) {
                PlayRemoteController.start(context, playlistId, name, list, mode)
            }
            if (generation != remoteStartGeneration) return@launch
            result
                .onSuccess { url -> remoteFlow = RemotePlayFlowState.Started(url, mode) }
                .onFailure { error ->
                    remoteFlow = null
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
                            RemotePlayIconButton(
                                active = remoteRunning,
                                onClick = {
                                    if (remoteRunning) {
                                        PlayRemoteController.stop()
                                        Toast.makeText(
                                            context,
                                            R.string.remote_stopped,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@RemotePlayIconButton
                                    }
                                    pendingRemotePlaylistId = AppPrefs.getLastPlaylistId(context)
                                    remoteFlow = RemotePlayFlowState.ChooseMode
                                },
                                onLongClick = if (remoteRunning) {
                                    { showRemoteDebug = true }
                                } else {
                                    null
                                },
                            )
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

    remoteFlow?.let { flow ->
        RemotePlayFlowDialog(
            state = flow,
            onCancel = { cancelRemoteFlow() },
            onCloseStarted = { closeRemoteStartedDialog() },
            onStopRemote = { stopRemoteFlow() },
            onSelectMode = { mode ->
                pendingRemotePlaylistId?.let { startRemote(it, mode) }
            },
        )
    }

    if (showRemoteDebug) {
        RemotePlayDebugDialog(onDismiss = { showRemoteDebug = false })
    }
}
