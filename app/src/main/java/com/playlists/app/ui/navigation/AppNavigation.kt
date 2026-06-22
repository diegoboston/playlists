package com.playlists.app.ui.navigation

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.playlists.app.R
import com.playlists.app.ui.AppUpdateUiState
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.components.AppUpdateBanner
import com.playlists.app.ui.screens.ImportSongScreen
import com.playlists.app.ui.screens.MainTabsScreen
import com.playlists.app.ui.screens.PlaylistDetailScreen
import com.playlists.app.ui.screens.PlaylistPlaybackScreen
import com.playlists.app.ui.screens.QuickstartScreen
import com.playlists.app.ui.screens.SongViewScreen
import com.playlists.app.util.AppUpdate
import java.io.File

object Routes {
    const val MAIN = "main"
    const val IMPORT = "import"
    const val QUICKSTART = "quickstart"
    const val SONG = "song/{songId}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val PLAYBACK = "playlist/{playlistId}/play"

    fun song(songId: Long) = "song/$songId"
    fun playlist(playlistId: Long) = "playlist/$playlistId"
    fun playback(playlistId: Long) = "playlist/$playlistId/play"
}

@Composable
fun AppNavigation(
    viewModel: PlaylistsViewModel,
    pendingInstallApk: (File) -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val updateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()

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
            is AppUpdateUiState.ReadyToInstall -> {
                pendingInstallApk(state.apk)
                viewModel.clearAppUpdateState()
            }
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
                    )
                }
                composable(Routes.IMPORT) {
                    ImportSongScreen(
                        viewModel = viewModel,
                        onBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Routes.MAIN)
                            }
                        },
                        onSaved = { songId ->
                            navController.popBackStack(Routes.MAIN, false)
                            navController.navigate(Routes.song(songId))
                        },
                    )
                }
                composable(Routes.QUICKSTART) {
                    QuickstartScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
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
                    SongViewScreen(
                        songId = songId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.PLAYLIST,
                    arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
                ) { entry ->
                    val playlistId = entry.arguments?.getLong("playlistId") ?: return@composable
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
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
                    PlaylistPlaybackScreen(
                        playlistId = playlistId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            updateState?.let { state ->
                if (state !is AppUpdateUiState.ReadyToInstall) {
                    AppUpdateBanner(
                        state = state,
                        onDismiss = { viewModel.clearAppUpdateState() },
                    )
                }
            }
        }
    }
}
