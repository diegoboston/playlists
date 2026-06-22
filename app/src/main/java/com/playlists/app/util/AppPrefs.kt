package com.playlists.app.util

import android.content.Context

object AppPrefs {
    private const val PREFS = "playlists_prefs"
    private const val KEY_REMOTE_PORT = "remote_port"
    private const val KEY_LAST_PLAYLIST_ID = "last_playlist_id"

    const val DEFAULT_REMOTE_PORT = 44444

    fun getRemotePort(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_REMOTE_PORT, DEFAULT_REMOTE_PORT)

    fun setRemotePort(context: Context, port: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_REMOTE_PORT, port)
            .apply()
    }

    fun getLastPlaylistId(context: Context): Long? {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_PLAYLIST_ID, -1L)
        return if (id >= 0) id else null
    }

    fun setLastPlaylistId(context: Context, playlistId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_PLAYLIST_ID, playlistId)
            .apply()
    }
}
