package com.playlists.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.playlists.app.R
import com.playlists.app.ui.AppUpdateUiState
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.components.AppUpdateBanner
import com.playlists.app.ui.screens.ChartAssistantScreen
import com.playlists.app.ui.screens.ImportSongScreen
import com.playlists.app.ui.screens.MainTabsScreen
import com.playlists.app.ui.screens.PlaylistDetailScreen
import com.playlists.app.ui.screens.PlaylistPlaybackScreen
import com.playlists.app.ui.screens.QuickstartScreen
import com.playlists.app.ui.screens.SettingsScreen
import com.playlists.app.ui.screens.ChartRetransposeScreen
import com.playlists.app.ui.screens.SongViewScreen
import com.playlists.app.util.AppUpdate
import java.io.File

object Routes {
    const val MAIN = "main"
    const val IMPORT = "import"
    const val QUICKSTART = "quickstart"
    const val SETTINGS = "settings"
    const val SONG = "song/{songId}"
    const val SONG_RETRANSPOSE = "song/{songId}/retranspose"
    const val PLAYLIST = "playlist/{playlistId}"
    const val PLAYBACK = "playlist/{playlistId}/play"
    const val CHART_ASSISTANT = "assistant"
    const val CHART_ASSISTANT_PLAYLIST = "playlist/{playlistId}/assistant"

    fun song(songId: Long) = "song/$songId"
    fun songRetranspose(songId: Long) = "song/$songId/retranspose"
    fun playlist(playlistId: Long) = "playlist/$playlistId"
    fun playback(playlistId: Long) = "playlist/$playlistId/play"
    fun chartAssistant(playlistId: Long? = null): String =
        if (playlistId != null) "playlist/$playlistId/assistant" else CHART_ASSISTANT
}

internal fun openPlaylistIdFromRoute(entry: NavBackStackEntry?): Long? {
    val route = entry?.destination?.route ?: return null
    return when (route) {
        Routes.PLAYLIST, Routes.PLAYBACK, Routes.CHART_ASSISTANT_PLAYLIST ->
            entry.arguments?.getLong("playlistId")?.takeIf { it > 0L }
        else -> null
    }
}

@Composable
private fun rememberGuardedBackHandler(
    entry: NavBackStackEntry,
    onBack: () -> Unit,
): () -> Unit {
    var handled by remember(entry) { mutableStateOf(false) }
    val currentOnBack by rememberUpdatedState(onBack)
    BackHandler {
        if (!handled) {
            handled = true
            currentOnBack()
        }
    }
    return remember(entry) {
        {
            if (!handled) {
                handled = true
                currentOnBack()
            }
        }
    }
}

private fun NavController.popBackToMain() {
    if (!popBackStack(Routes.MAIN, inclusive = false)) {
        if (currentDestination?.route != Routes.MAIN) {
            navigate(Routes.MAIN) {
                popUpTo(Routes.MAIN) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

@Composable
fun AppNavigation(
    viewModel: PlaylistsViewModel,
    pendingInstallApk: (File) -> Unit,
    retryInstallApk: (File) -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val onSettingsScreen = navBackStackEntry?.destination?.route == Routes.SETTINGS
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val updateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val pendingChartImport by viewModel.pendingChartImport.collectAsStateWithLifecycle()

    LaunchedEffect(navBackStackEntry) {
        viewModel.setOpenPlaylistId(openPlaylistIdFromRoute(navBackStackEntry))
    }

    LaunchedEffect(pendingImport) {
        if (pendingImport != null && navController.currentDestination?.route != Routes.IMPORT) {
            navController.navigate(Routes.IMPORT)
        }
    }

    LaunchedEffect(pendingChartImport) {
        val pending = pendingChartImport ?: return@LaunchedEffect
        navController.navigate(Routes.chartAssistant(pending.playlistId)) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(Unit) {
        val versionName = viewModel.fetchLaunchUpdateVersion(context)
        if (versionName != null) {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.update_app_snackbar_prompt, versionName),
                actionLabel = context.getString(R.string.update_app_snackbar_action),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.startAppUpdateDownload(context)
            }
        }
    }

    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is AppUpdateUiState.ReadyToInstall -> pendingInstallApk(state.apk)
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.MAIN,
            ) {
                composable(Routes.MAIN) {
                    MainTabsScreen(
                        viewModel = viewModel,
                        onOpenSong = { navController.navigate(Routes.song(it)) },
                        onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                        onNewKey = { navController.navigate(Routes.songRetranspose(it)) },
                        onQuickstart = { navController.navigate(Routes.QUICKSTART) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.IMPORT) { entry ->
                    val onBack = rememberGuardedBackHandler(entry) {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.MAIN)
                        }
                    }
                    ImportSongScreen(
                        viewModel = viewModel,
                        onBack = onBack,
                        onSaved = { songId ->
                            navController.popBackStack(Routes.MAIN, false)
                            navController.navigate(Routes.song(songId))
                        },
                    )
                }
                composable(Routes.SETTINGS) { entry ->
                    val onBack = rememberGuardedBackHandler(entry) {
                        navController.popBackToMain()
                    }
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = onBack,
                        onInstallUpdate = retryInstallApk,
                    )
                }
                composable(Routes.QUICKSTART) { entry ->
                    val onBack = rememberGuardedBackHandler(entry) {
                        navController.popBackToMain()
                    }
                    QuickstartScreen(
                        viewModel = viewModel,
                        onBack = onBack,
                        onOpenPlaylist = { id ->
                            navController.popBackStack(Routes.MAIN, false)
                            navController.navigate(Routes.playlist(id))
                        },
                    )
                }
                composable(
                    route = Routes.SONG,
                    arguments = listOf(navArgument("songId") { type = NavType.LongType }),
                ) { entry ->
                    val songId = entry.arguments?.getLong("songId") ?: return@composable
                    val onBack = rememberGuardedBackHandler(entry) {
                        navController.popBackStack()
                    }
                    SongViewScreen(
                        songId = songId,
                        viewModel = viewModel,
                        onBack = onBack,
                        onNewKey = { navController.navigate(Routes.songRetranspose(it)) },
                    )
                }
                composable(
                    route = Routes.SONG_RETRANSPOSE,
                    arguments = listOf(navArgument("songId") { type = NavType.LongType }),
                ) { entry ->
                    val songId = entry.arguments?.getLong("songId") ?: return@composable
                    val onBack = rememberGuardedBackHandler(entry) {
                        navController.popBackStack()
                    }
                    ChartRetransposeScreen(
                        songId = songId,
                        onBack = onBack,
                        onSaved = {
                            navController.popBackStack()
                        },
                    )
                }
                composable(
                    route = Routes.PLAYLIST,
                    arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
                ) { entry ->
                    val playlistId = entry.arguments?.getLong("playlistId") ?: return@composable
                    val onBack = rememberGuardedBackHandler(entry) {
                        navController.popBackToMain()
                    }
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        viewModel = viewModel,
                        onBack = onBack,
                        onPlay = { navController.navigate(Routes.playback(it)) },
                        onOpenSong = { navController.navigate(Routes.song(it)) },
                        onNavigateToDuplicate = { newId ->
                            navController.popBackStack()
                            navController.navigate(Routes.playlist(newId))
                        },
                        onFindChart = { navController.navigate(Routes.chartAssistant(it)) },
                    )
                }
                composable(Routes.CHART_ASSISTANT) { entry ->
                    val onBack = rememberGuardedBackHandler(entry) {
                        navController.popBackStack()
                    }
                    ChartAssistantScreen(
                        playlistId = null,
                        playlistsViewModel = viewModel,
                        onBack = onBack,
                        onSaved = { songId ->
                            navController.popBackStack()
                            navController.navigate(Routes.song(songId))
                        },
                    )
                }
                composable(
                    route = Routes.CHART_ASSISTANT_PLAYLIST,
                    arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
                ) { entry ->
                    val playlistId = entry.arguments?.getLong("playlistId") ?: return@composable
                    val onBack = rememberGuardedBackHandler(entry) {
                        navController.popBackStack()
                    }
                    ChartAssistantScreen(
                        playlistId = playlistId,
                        playlistsViewModel = viewModel,
                        onBack = onBack,
                        onSaved = { songId ->
                            navController.popBackStack()
                            navController.navigate(Routes.song(songId))
                        },
                    )
                }
                composable(
                    route = Routes.PLAYBACK,
                    arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
                ) { entry ->
                    val playlistId = entry.arguments?.getLong("playlistId") ?: return@composable
                    val onBack = rememberGuardedBackHandler(entry) {
                        navController.popBackStack()
                    }
                    PlaylistPlaybackScreen(
                        playlistId = playlistId,
                        viewModel = viewModel,
                        onBack = onBack,
                    )
                }
            }

            if (!onSettingsScreen) {
                updateState?.let { state ->
                    AppUpdateBanner(
                        state = state,
                        onDismiss = { viewModel.clearAppUpdateState() },
                        onInstall = { apk -> retryInstallApk(apk) },
                    )
                }
            }
        }
    }
}
