package com.playlists.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playlists.app.util.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class AppUpdateUiState {
    data object Checking : AppUpdateUiState()
    data class Downloading(val progress: Float?) : AppUpdateUiState()
    data class UpToDate(val versionName: String) : AppUpdateUiState()
    data class ReadyToInstall(val apk: File, val versionName: String) : AppUpdateUiState()
    data class Failed(val message: String) : AppUpdateUiState()
}

class MainViewModel : ViewModel() {
    private val _appUpdateState = MutableStateFlow<AppUpdateUiState?>(null)
    val appUpdateState: StateFlow<AppUpdateUiState?> = _appUpdateState.asStateFlow()

    private var launchUpdatePromptHandled = false

    fun checkLaunchUpdateAvailable(context: android.content.Context, onResult: (String?) -> Unit) {
        if (launchUpdatePromptHandled) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                val local = AppUpdate.installedVersionCode(context)
                val release = AppUpdate.fetchLatestRelease()
                if (release.versionCode > local) release.versionName else null
            } catch (_: Exception) {
                null
            }
            launchUpdatePromptHandled = true
            withContext(Dispatchers.Main.immediate) { onResult(result) }
        }
    }

    fun clearAppUpdateState() {
        _appUpdateState.value = null
    }

    fun startAppUpdateDownload(context: android.content.Context) {
        val current = _appUpdateState.value
        if (current is AppUpdateUiState.Checking || current is AppUpdateUiState.Downloading) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _appUpdateState.value = AppUpdateUiState.Checking
            try {
                val local = AppUpdate.installedVersionCode(context)
                val release = AppUpdate.fetchLatestRelease()
                if (release.versionCode <= local) {
                    AppUpdate.clearUpdateCache(context)
                    _appUpdateState.value = AppUpdateUiState.UpToDate(release.versionName)
                    return@launch
                }
                _appUpdateState.value = AppUpdateUiState.Downloading(null)
                val apk = AppUpdate.downloadApk(context, release.downloadUrl) { frac ->
                    _appUpdateState.value = AppUpdateUiState.Downloading(frac)
                }
                _appUpdateState.value = AppUpdateUiState.ReadyToInstall(apk, release.versionName)
            } catch (e: Exception) {
                AppUpdate.clearUpdateCache(context)
                _appUpdateState.value = AppUpdateUiState.Failed(e.message ?: e.toString())
            }
        }
    }
}
