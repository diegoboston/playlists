package com.playlists.app

import android.app.Application
import android.util.Log
import com.playlists.app.data.AppDatabase
import com.playlists.app.data.PlaylistRepository
import com.playlists.app.data.SongRepository
import com.playlists.app.util.AppUpdate
import com.playlists.app.util.SongFilenameMigration
import com.playlists.app.util.StorageMigration
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

    private var dataInitialized = false

    override fun onCreate() {
        super.onCreate()
        ensureDataInitialized()

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

    fun ensureDataInitialized() {
        if (dataInitialized) return
        synchronized(this) {
            if (dataInitialized) return
            StorageMigration.runIfNeeded(this)
            val db = AppDatabase.get(this)
            songRepository = SongRepository(db.songDao())
            playlistRepository = PlaylistRepository(db.playlistDao(), db.playlistSongDao())
            runBlocking {
                SongFilenameMigration.runIfNeeded(this@PlaylistsApp, songRepository)
            }
            dataInitialized = true
        }
    }

    fun reinitializeAfterStorageMigration() {
        synchronized(this) {
            AppDatabase.closeAndReset()
            dataInitialized = false
        }
        ensureDataInitialized()
    }

    companion object {
        private const val TAG = "PlaylistsApp"
        private const val PREFS = "playlists_prefs"
        private const val KEY_LAST_VERSION = "last_installed_version_code"

        fun from(app: Application): PlaylistsApp = app as PlaylistsApp
    }
}
