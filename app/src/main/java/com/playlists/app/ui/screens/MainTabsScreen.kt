package com.playlists.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.playlists.app.remote.RemotePlayStartedDialog
import com.playlists.app.remote.RemotePlayErrorDialog
import com.playlists.app.remote.RemotePlayErrors
import com.playlists.app.remote.RemotePlayFlowDialog
import com.playlists.app.remote.RemotePlayFlowState
import com.playlists.app.remote.RemotePlayMode
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.components.MainOverflowMenu
import com.playlists.app.ui.components.PianoDialog
import com.playlists.app.ui.rememberOpenAiKeyReady
import com.playlists.app.util.AppPrefs
import com.playlists.app.util.CaptureImageContract
import com.playlists.app.util.CaptureImageStore
import com.playlists.app.util.CapturePageStart
import com.playlists.app.util.LocalFileImport
import com.playlists.app.util.PendingPageAdjust
import com.playlists.app.util.ScanImportOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabsScreen(
    viewModel: PlaylistsViewModel,
    onOpenSong: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onNewKey: (Long) -> Unit,
    onQuickstart: () -> Unit,
    onSettings: () -> Unit,
    onFindChart: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val remoteRunning by PlayRemoteController.running.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var remoteFlow by remember { mutableStateOf<RemotePlayFlowState?>(null) }
    var remoteStartGeneration by remember { mutableIntStateOf(0) }
    var showRemoteDebug by remember { mutableStateOf(false) }
    var showPiano by remember { mutableStateOf(false) }
    var readingScanTitle by remember { mutableStateOf(false) }
    val chartSearchReady = rememberOpenAiKeyReady()
    val latestChartSearchReady by rememberUpdatedState(chartSearchReady)
    val canTakePhoto = remember(context) { CaptureImageStore.hasCameraApp(context) }

    fun applyScanOutcome(outcome: ScanImportOutcome) {
        val pending = outcome.pending
        if (pending == null) {
            Toast.makeText(context, R.string.import_file_failed, Toast.LENGTH_LONG).show()
            return
        }
        if (outcome.ocrFailed) {
            Toast.makeText(context, R.string.scan_image_ocr_failed, Toast.LENGTH_SHORT).show()
        }
        viewModel.setPendingImport(pending)
    }

    fun importScanned(load: () -> ScanImportOutcome) {
        scope.launch {
            if (latestChartSearchReady) readingScanTitle = true
            try {
                applyScanOutcome(withContext(Dispatchers.IO) { load() })
            } finally {
                readingScanTitle = false
            }
        }
    }

    val scanImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importScanned { LocalFileImport.fromGalleryImage(context, uri) }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        CaptureImageContract(),
    ) { success ->
        if (!success) return@rememberLauncherForActivityResult
        scope.launch {
            val adjustEnabled = AppPrefs.isAdjustPageEnabled(context)
            if (!adjustEnabled && latestChartSearchReady) readingScanTitle = true
            try {
                when (
                    val start = withContext(Dispatchers.IO) {
                        LocalFileImport.beginCapturedPage(
                            context,
                            CaptureImageStore.pendingFile(context),
                        )
                    }
                ) {
                    CapturePageStart.Failed -> {
                        Toast.makeText(context, R.string.import_file_failed, Toast.LENGTH_LONG).show()
                    }
                    is CapturePageStart.NeedsAdjust -> {
                        viewModel.setPendingPageAdjust(
                            PendingPageAdjust(start.stored.absolutePath, existingImport = null),
                        )
                    }
                    is CapturePageStart.Ready -> applyScanOutcome(start.outcome)
                }
            } finally {
                readingScanTitle = false
            }
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val pending = withContext(Dispatchers.IO) {
                LocalFileImport.fromDocument(context, uri)
            }
            if (pending == null) {
                Toast.makeText(context, R.string.import_file_failed, Toast.LENGTH_LONG).show()
            } else {
                viewModel.setPendingImport(pending)
            }
        }
    }

    val startupPlaylistId =
        if (remoteRunning) PlayRemoteController.startupPlaylistId() else null
    val entries by viewModel.observePlaylistSongs(startupPlaylistId ?: 0L)
        .collectAsStateWithLifecycle()

    LaunchedEffect(entries, remoteRunning, startupPlaylistId) {
        val list = entries ?: return@LaunchedEffect
        if (!remoteRunning || startupPlaylistId == null) return@LaunchedEffect
        PlayRemoteController.refreshSongs(startupPlaylistId, list)
    }

    fun cancelRemoteFlow() {
        remoteStartGeneration++
        remoteFlow = null
        scope.launch(Dispatchers.IO) { PlayRemoteController.stop() }
    }

    fun closeRemoteStartedDialog() {
        remoteFlow = null
    }

    fun stopRemoteFlow() {
        cancelRemoteFlow()
        showRemoteDebug = false
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
                PlayRemoteController.start(
                    context = context,
                    playlistId = playlistId,
                    playlistName = name,
                    entries = list,
                    mode = mode,
                    onPhase = { phase ->
                        scope.launch(Dispatchers.Main.immediate) {
                            if (generation == remoteStartGeneration) {
                                remoteFlow = RemotePlayFlowState.Starting(mode, phase)
                            }
                        }
                    },
                )
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
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    MainOverflowMenu(
                        showTakeImage = canTakePhoto,
                        showAiSearch = chartSearchReady,
                        showWebServer = true,
                        webServerActive = remoteRunning,
                        onTakeImage = {
                            val launched = runCatching {
                                takePictureLauncher.launch(
                                    CaptureImageStore.prepareNewCapture(context),
                                )
                            }.isSuccess
                            if (!launched) {
                                Toast.makeText(
                                    context,
                                    R.string.scan_image_camera_failed,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onImportFromGallery = {
                            scanImageLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        onImportFromStorage = {
                            importFileLauncher.launch(arrayOf("image/*", "application/pdf"))
                        },
                        onAiSearch = onFindChart,
                        onWebServer = {
                            if (remoteRunning) {
                                showRemoteDebug = true
                            } else {
                                remoteFlow = RemotePlayFlowState.ChooseMode
                            }
                        },
                        onPiano = { showPiano = true },
                        onSettings = onSettings,
                    )
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
                0 -> SongsScreen(viewModel, onOpenSong, onNewKey)
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
                startRemote(AppPrefs.getLastPlaylistId(context), mode)
            },
        )
    }

    if (showRemoteDebug) {
        val statusMode = PlayRemoteController.sessionSnapshot()?.mode ?: RemotePlayMode.CLOUDFLARE
        RemotePlayStartedDialog(
            mode = statusMode,
            titleRes = R.string.remote_debug_title,
            onDismiss = { showRemoteDebug = false },
            onStop = {
                showRemoteDebug = false
                stopRemoteFlow()
            },
        )
    }

    if (showPiano) {
        PianoDialog(onDismiss = { showPiano = false })
    }

    if (readingScanTitle) {
        BasicAlertDialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(
                        stringResource(R.string.scan_image_reading_title),
                    )
                }
            }
        }
    }
}
