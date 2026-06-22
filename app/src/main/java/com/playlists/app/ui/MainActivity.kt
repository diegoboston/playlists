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
import androidx.lifecycle.LifecycleEventObserver
import com.playlists.app.PlaylistsApp
import com.playlists.app.R
import com.playlists.app.ui.navigation.AppNavigation
import com.playlists.app.ui.screens.StorageAccessScreen
import com.playlists.app.ui.theme.PlaylistsTheme
import com.playlists.app.util.AppUpdate
import com.playlists.app.util.ShareImporter
import com.playlists.app.util.StageManagerStorage
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: PlaylistsViewModel by viewModels()
    private var pendingApkInstall: File? = null
    private var installPermissionRequestPending = false
    private var storageReady by mutableStateOf(false)
    private var storageSettingsPending = false

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
    ) { granted ->
        if (granted) {
            onStorageAccessGranted()
        }
    }

    private val storageSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        storageSettingsPending = false
        onStorageAccessGranted()
    }

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            if (storageSettingsPending) {
                onStorageAccessGranted()
            } else if (!storageReady && StageManagerStorage.hasAccess(this@MainActivity)) {
                onStorageAccessGranted()
            }
            tryLaunchPendingInstall()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(lifecycleObserver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        storageReady = StageManagerStorage.hasAccess(this)
        if (storageReady) {
            (application as PlaylistsApp).ensureDataInitialized()
        }
        handleShareIntent(intent)

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

    override fun onDestroy() {
        lifecycle.removeObserver(lifecycleObserver)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun onStorageAccessGranted() {
        if (!StageManagerStorage.hasAccess(this)) return
        val wasReady = storageReady
        (application as PlaylistsApp).reinitializeAfterStorageMigration()
        storageReady = true
        if (!wasReady) {
            recreate()
        }
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storageSettingsPending = true
            storageSettingsLauncher.launch(StageManagerStorage.manageStorageIntent(this))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            onStorageAccessGranted()
            return
        }
        legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null || !storageReady) return
        val pending = ShareImporter.parseIntent(this, intent) ?: return
        viewModel.setPendingImport(pending)
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
