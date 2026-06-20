package com.playlists.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.playlists.app.R
import com.playlists.app.databinding.ActivityMainBinding
import com.playlists.app.util.AppUpdate
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private var pendingApkInstall: File? = null

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingApkInstall?.let { apk ->
            if (packageManager.canRequestPackageInstalls()) {
                installAppUpdate(apk)
            } else {
                Toast.makeText(this, R.string.update_app_needs_permission, Toast.LENGTH_LONG).show()
            }
            pendingApkInstall = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.viewPager.adapter = TabsAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_songs)
                else -> getString(R.string.tab_playlists)
            }
        }.attach()

        observeAppUpdateState()
        checkForUpdateOnLaunch()
    }

    private fun checkForUpdateOnLaunch() {
        lifecycleScope.launch {
            vm.checkLaunchUpdateAvailable(this@MainActivity) { versionName ->
                if (versionName == null) return@checkLaunchUpdateAvailable
                Snackbar.make(
                    binding.root,
                    getString(R.string.update_app_snackbar_prompt, versionName),
                    Snackbar.LENGTH_LONG,
                )
                    .setAction(R.string.update_app_snackbar_action) {
                        vm.startAppUpdateDownload(this@MainActivity)
                    }
                    .show()
            }
        }
    }

    private fun observeAppUpdateState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.appUpdateState.collect { state ->
                    renderUpdateBanner(state)
                    when (state) {
                        is AppUpdateUiState.UpToDate -> {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.update_app_up_to_date, state.versionName),
                                Snackbar.LENGTH_SHORT,
                            ).show()
                            vm.clearAppUpdateState()
                        }
                        is AppUpdateUiState.ReadyToInstall -> {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.update_app_ready, state.versionName),
                                Snackbar.LENGTH_SHORT,
                            ).show()
                            installAppUpdate(state.apk)
                            vm.clearAppUpdateState()
                        }
                        is AppUpdateUiState.Failed -> {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.update_app_failed, state.message),
                                Snackbar.LENGTH_LONG,
                            ).show()
                            vm.clearAppUpdateState()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun renderUpdateBanner(state: AppUpdateUiState?) {
        when (state) {
            AppUpdateUiState.Checking -> {
                binding.updateBanner.visibility = View.VISIBLE
                binding.updateBannerText.setText(R.string.update_app_checking)
                binding.updateProgress.isIndeterminate = true
            }
            is AppUpdateUiState.Downloading -> {
                binding.updateBanner.visibility = View.VISIBLE
                val progress = state.progress
                if (progress != null) {
                    val percent = (progress * 100f).toInt().coerceIn(0, 100)
                    binding.updateBannerText.text =
                        getString(R.string.update_app_downloading_percent, percent)
                    binding.updateProgress.isIndeterminate = false
                    binding.updateProgress.progress = percent
                } else {
                    binding.updateBannerText.setText(R.string.update_app_downloading)
                    binding.updateProgress.isIndeterminate = true
                }
            }
            else -> binding.updateBanner.visibility = View.GONE
        }
    }

    private fun installAppUpdate(apk: File) {
        when (AppUpdate.launchInstaller(this, apk)) {
            AppUpdate.InstallResult.Launched -> Unit
            AppUpdate.InstallResult.NeedsPermission -> {
                pendingApkInstall = apk
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                )
                installPermissionLauncher.launch(intent)
            }
        }
    }

    private class TabsAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount() = 2
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> SongsFragment()
            else -> PlaylistsFragment()
        }
    }
}
