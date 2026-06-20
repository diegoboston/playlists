package com.playlists.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import com.playlists.app.data.AppDatabase
import com.playlists.app.data.PlaylistRepository
import com.playlists.app.data.SongRepository
import com.playlists.app.util.AppUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlaylistsApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    lateinit var songRepository: SongRepository
        private set
    lateinit var playlistRepository: PlaylistRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        songRepository = SongRepository(db.songDao())
        playlistRepository = PlaylistRepository(db.playlistDao(), db.playlistSongDao())

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

    companion object {
        private const val TAG = "PlaylistsApp"
        private const val PREFS = "playlists_prefs"
        private const val KEY_LAST_VERSION = "last_installed_version_code"

        fun from(app: Application): PlaylistsApp = app as PlaylistsApp
    }
}
