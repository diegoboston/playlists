package com.playlists.app.ui

import java.io.File

sealed class AppUpdateUiState {
    data object Checking : AppUpdateUiState()
    data class Downloading(val progress: Float?) : AppUpdateUiState()
    data class UpToDate(val versionName: String) : AppUpdateUiState()
    data class ReadyToInstall(val apk: File, val versionName: String) : AppUpdateUiState()
    data class Failed(val message: String) : AppUpdateUiState()
}
