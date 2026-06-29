package com.playlists.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

object StageManagerStorage {
    const val ROOT_DIR_NAME = "StageManager"
    const val SONGS_DIR_NAME = "songs"
    const val DB_FILE_NAME = "playlists.db"
    const val STATE_FILE_NAME = "state.json"

    @Volatile
    private var songsDirOverride: File? = null

    /** Test hook — redirect [songsDir] to a temp folder. */
    internal fun setSongsDirForTests(dir: File?) {
        songsDirOverride = dir
    }

    fun root(): File =
        songsDirOverride?.parentFile
            ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), ROOT_DIR_NAME)

    fun songsDir(): File = songsDirOverride ?: File(root(), SONGS_DIR_NAME)

    fun dbFile(): File = File(root(), DB_FILE_NAME)

    fun stateFile(): File = File(root(), STATE_FILE_NAME)

    fun hasAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun manageStorageIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
    }

    fun ensureDirectories(): Boolean {
        return runCatching {
            root().mkdirs()
            songsDir().mkdirs()
            root().isDirectory && songsDir().isDirectory
        }.getOrDefault(false)
    }

    /** Total bytes under the StageManager library folder (songs, DB, chart sidecars, state). */
    fun librarySizeBytes(): Long = directorySizeBytes(root())

    internal fun directorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { directorySizeBytes(it) } ?: 0L
    }
}
