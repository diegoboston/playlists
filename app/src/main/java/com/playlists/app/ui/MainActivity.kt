package com.playlists.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.playlists.app.R
import com.playlists.app.ui.navigation.AppNavigation
import com.playlists.app.ui.theme.PlaylistsTheme
import com.playlists.app.util.AppUpdate
import com.playlists.app.util.ShareImporter
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: PlaylistsViewModel by viewModels()
    private var pendingApkInstall: File? = null

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        pendingApkInstall?.let { apk ->
            if (packageManager.canRequestPackageInstalls()) {
                AppUpdate.launchInstaller(this, apk)
            } else {
                Toast.makeText(this, R.string.update_app_needs_permission, Toast.LENGTH_LONG).show()
            }
            pendingApkInstall = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)

        setContent {
            PlaylistsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        viewModel = viewModel,
                        pendingInstallApk = { apk -> launchInstall(apk) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val pending = ShareImporter.parseIntent(this, intent) ?: return
        viewModel.setPendingImport(pending)
    }

    private fun launchInstall(apk: File) {
        when (AppUpdate.launchInstaller(this, apk)) {
            AppUpdate.InstallResult.Launched -> Unit
            AppUpdate.InstallResult.NeedsPermission -> {
                pendingApkInstall = apk
                installPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName"),
                    ),
                )
            }
        }
    }
}
