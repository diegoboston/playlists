package com.playlists.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.remote.PlayRemoteController
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
    var remoteUrl by remember { mutableStateOf<String?>(null) }
    var remotePlaylistName by remember { mutableStateOf<String?>(null) }

    val activePlaylistId = PlayRemoteController.activePlaylistId
    val entries by viewModel.observePlaylistSongs(activePlaylistId ?: -1L)
        .collectAsStateWithLifecycle()

    LaunchedEffect(remoteRunning, activePlaylistId) {
        if (remoteRunning && activePlaylistId != null) {
            remoteUrl = PlayRemoteController.currentUrl()
            remotePlaylistName = viewModel.getPlaylist(activePlaylistId)?.name
        } else {
            remoteUrl = null
            remotePlaylistName = null
        }
    }

    LaunchedEffect(entries, remoteRunning, activePlaylistId) {
        if (remoteRunning && activePlaylistId != null && PlayRemoteController.isRunningFor(activePlaylistId)) {
            PlayRemoteController.refreshSongs(entries)
        }
    }

    fun startRemote(playlistId: Long) {
        scope.launch {
            val playlist = viewModel.getPlaylist(playlistId)
            if (playlist == null) {
                Toast.makeText(context, R.string.remote_playlist_gone, Toast.LENGTH_LONG).show()
                return@launch
            }
            val list = viewModel.getPlaylistSongs(playlistId)
            if (list.isEmpty()) {
                Toast.makeText(context, R.string.remote_empty, Toast.LENGTH_LONG).show()
                return@launch
            }
            PlayRemoteController.start(context, playlistId, playlist.name, list)
                .onSuccess { url ->
                    remoteUrl = url
                    remotePlaylistName = playlist.name
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    Toast.makeText(context, R.string.remote_started, Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    val message = when {
                        error.message?.contains("LAN IP") == true ->
                            context.getString(R.string.remote_no_network)
                        else -> context.getString(R.string.remote_failed, error.message ?: "unknown")
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
                        Row {
                            IconButton(
                                onClick = {
                                    if (remoteRunning) {
                                        PlayRemoteController.currentUrl()?.let { url ->
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                        return@IconButton
                                    }
                                    val playlistId = AppPrefs.getLastPlaylistId(context)
                                    if (playlistId == null) {
                                        Toast.makeText(
                                            context,
                                            R.string.remote_no_last_playlist,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                        return@IconButton
                                    }
                                    startRemote(playlistId)
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
                            if (remoteRunning) {
                                IconButton(onClick = {
                                    PlayRemoteController.stop()
                                    remoteUrl = null
                                    remotePlaylistName = null
                                    Toast.makeText(context, R.string.remote_stopped, Toast.LENGTH_SHORT).show()
                                }) {
                                    Text(
                                        stringResource(R.string.remote_stop),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
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
            if (remoteUrl != null) {
                Text(
                    text = buildString {
                        remotePlaylistName?.let { append("$it · ") }
                        append(stringResource(R.string.remote_url_label, remoteUrl!!))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(12.dp),
                )
            }
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
}
