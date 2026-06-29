package com.playlists.app

import android.app.Application
import android.util.Log
import com.playlists.app.data.AppDatabase
import com.playlists.app.data.PlaylistRepository
import com.playlists.app.data.SongRepository
import com.playlists.app.util.AppIconManager
import com.playlists.app.util.AppUpdate
import com.playlists.app.util.SongFileMigration
import com.playlists.app.util.StageManagerState
import com.playlists.app.util.StageManagerStorage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PlaylistsApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var songRepository: SongRepository
        private set
    lateinit var playlistRepository: PlaylistRepository
        private set

    private var initialized = false

    override fun onCreate() {
        super.onCreate()
        AppIconManager.applySaved(this)
        applicationScope.launch {
            runCatching {
                val installed = AppUpdate.installedVersionCode(this@PlaylistsApp)
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                val previous = prefs.getLong(KEY_LAST_VERSION, -1L)
                if (previous >= 0 && installed > previous) {
                    AppUpdate.clearUpdateCache(this@PlaylistsApp)
                }
                prefs.edit().putLong(KEY_LAST_VERSION, installed).apply()
            }.onFailure { Log.w(TAG, "Update cache cleanup failed", it) }
        }
    }

    fun initialize() {
        check(StageManagerStorage.hasAccess(this)) { "All files access required" }
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            StageManagerStorage.ensureDirectories()
            StageManagerState.exportFromSharedPreferences(this)
            PDFBoxResourceLoader.init(applicationContext)
            val db = AppDatabase.get(this)
            val songDao = db.songDao()
            runBlocking { SongFileMigration.sync(songDao) }
            songRepository = SongRepository(songDao)
            playlistRepository = PlaylistRepository(db.playlistDao(), db.playlistSongDao())
            initialized = true
        }
    }

    companion object {
        private const val TAG = "PlaylistsApp"
        private const val PREFS = "playlists_prefs"
        private const val KEY_LAST_VERSION = "last_installed_version_code"

        fun from(app: Application): PlaylistsApp = app as PlaylistsApp
    }
}
