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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.playlists.app.R
import com.playlists.app.ui.AppUpdateUiState
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.components.AppUpdateBanner
import com.playlists.app.ui.components.OrphanSongFilesDialog
import com.playlists.app.ui.screens.ImportSongScreen
import com.playlists.app.ui.screens.MainTabsScreen
import com.playlists.app.ui.screens.PlaylistDetailScreen
import com.playlists.app.ui.screens.PlaylistPlaybackScreen
import com.playlists.app.ui.screens.QuickstartScreen
import com.playlists.app.ui.screens.SettingsScreen
import com.playlists.app.ui.screens.SongViewScreen
import com.playlists.app.util.AppUpdate
import java.io.File

object Routes {
    const val MAIN = "main"
    const val IMPORT = "import"
    const val QUICKSTART = "quickstart"
    const val SETTINGS = "settings"
    const val SONG = "song/{songId}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val PLAYBACK = "playlist/{playlistId}/play"

    fun song(songId: Long) = "song/$songId"
    fun playlist(playlistId: Long) = "playlist/$playlistId"
    fun playback(playlistId: Long) = "playlist/$playlistId/play"
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
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val updateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val orphanSongFiles by viewModel.orphanSongFiles.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.scanOrphanSongFiles()
    }

    LaunchedEffect(pendingImport) {
        if (pendingImport != null && navController.currentDestination?.route != Routes.IMPORT) {
            navController.navigate(Routes.IMPORT)
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

            updateState?.let { state ->
                AppUpdateBanner(
                    state = state,
                    onDismiss = { viewModel.clearAppUpdateState() },
                    onInstall = { apk -> retryInstallApk(apk) },
                )
            }

            orphanSongFiles?.let { files ->
                OrphanSongFilesDialog(
                    files = files,
                    onDelete = { viewModel.dismissOrphanSongFiles(keepFiles = false) },
                    onKeep = { viewModel.dismissOrphanSongFiles(keepFiles = true) },
                )
            }
        }
    }
}
