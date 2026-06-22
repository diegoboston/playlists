package com.playlists.app.util

import android.content.Context

object AppPrefs {
    private const val PREFS = "playlists_prefs"
    private const val KEY_REMOTE_CODE = "remote_code"
    private const val KEY_REMOTE_PORT = "remote_port"
    private const val KEY_REMOTE_PIN = "remote_pin"
    private const val KEY_LAST_PLAYLIST_ID = "last_playlist_id"

    const val DEFAULT_REMOTE_CODE = 44444
    const val REMOTE_CODE_MIN = 10000
    const val REMOTE_CODE_MAX = 65535

    fun getRemoteCode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_REMOTE_CODE)) {
            return prefs.getInt(KEY_REMOTE_CODE, DEFAULT_REMOTE_CODE)
                .coerceIn(REMOTE_CODE_MIN, REMOTE_CODE_MAX)
        }
        val legacyPort = prefs.getInt(KEY_REMOTE_PORT, DEFAULT_REMOTE_CODE)
        if (legacyPort in REMOTE_CODE_MIN..REMOTE_CODE_MAX) {
            return legacyPort
        }
        return DEFAULT_REMOTE_CODE
    }

    fun setRemoteCode(context: Context, code: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_REMOTE_CODE, code)
            .remove(KEY_REMOTE_PORT)
            .remove(KEY_REMOTE_PIN)
            .apply()
    }

    fun getRemotePort(context: Context): Int = getRemoteCode(context)

    fun getRemotePin(context: Context): String = getRemoteCode(context).toString()

    fun isValidRemoteCode(text: String): Boolean {
        if (text.length != 5 || !text.all { it.isDigit() }) return false
        val value = text.toIntOrNull() ?: return false
        return value in REMOTE_CODE_MIN..REMOTE_CODE_MAX
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
