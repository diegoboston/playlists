package com.playlists.app

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import com.playlists.app.data.AppDatabase
import com.playlists.app.data.PlaylistRepository
import com.playlists.app.data.SongRepository

class PlaylistsApp : Application() {
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
    }

    companion object {
        fun from(app: Application): PlaylistsApp = app as PlaylistsApp
    }
}
