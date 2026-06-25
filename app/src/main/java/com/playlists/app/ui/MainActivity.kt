package com.playlists.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.playlists.app.PlaylistsApp
import com.playlists.app.R
import com.playlists.app.ui.navigation.AppNavigation
import com.playlists.app.ui.screens.StorageAccessScreen
import com.playlists.app.ui.theme.PlaylistsTheme
import com.playlists.app.util.AppUpdate
import com.playlists.app.util.ShareImporter
import com.playlists.app.util.SharePayload
import com.playlists.app.util.StageManagerStorage
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: PlaylistsViewModel by viewModels()
    private var pendingApkInstall: File? = null
    private var installPermissionRequestPending = false
    private var storageReady by mutableStateOf(false)

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        installPermissionRequestPending = false
        if (pendingApkInstall == null) return@registerForActivityResult
        if (!packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, R.string.update_app_needs_permission, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        tryLaunchPendingInstall()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) tryEnableStorage() }

    private val storageSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { tryEnableStorage() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        tryEnableStorage()
        setContent {
            PlaylistsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (storageReady) {
                        AppNavigation(
                            viewModel = viewModel,
                            pendingInstallApk = { apk -> queueInstall(apk) },
                            retryInstallApk = { apk -> queueInstall(apk) },
                        )
                    } else {
                        StorageAccessScreen(onOpenSettings = { requestStorageAccess() })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tryEnableStorage()
        tryLaunchPendingInstall()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun tryEnableStorage() {
        if (!StageManagerStorage.hasAccess(this) || storageReady) return
        (application as PlaylistsApp).initialize()
        storageReady = true
        handleShareIntent(intent)
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storageSettingsLauncher.launch(StageManagerStorage.manageStorageIntent(this))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            tryEnableStorage()
            return
        }
        legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null || !storageReady) return
        when (val payload = ShareImporter.parseIntent(this, intent) ?: return) {
            is SharePayload.FileImport -> viewModel.setPendingImport(payload.pending)
            is SharePayload.ChartUrl -> viewModel.setPendingChartImport(payload.url, payload.titleHint)
        }
    }

    private fun queueInstall(apk: File) {
        pendingApkInstall = apk
        tryLaunchPendingInstall()
    }

    private fun tryLaunchPendingInstall() {
        val apk = pendingApkInstall ?: return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        when (val result = AppUpdate.launchInstaller(this, apk)) {
            AppUpdate.InstallResult.Launched -> {
                pendingApkInstall = null
                installPermissionRequestPending = false
                viewModel.clearAppUpdateState()
            }
            AppUpdate.InstallResult.NeedsPermission -> {
                if (!installPermissionRequestPending) {
                    installPermissionRequestPending = true
                    installPermissionLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                }
            }
            is AppUpdate.InstallResult.Failed -> {
                pendingApkInstall = null
                installPermissionRequestPending = false
                viewModel.failAppUpdateInstall(result.message)
                Toast.makeText(
                    this,
                    getString(R.string.update_app_failed, result.message),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
