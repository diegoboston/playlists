package com.playlists.app.util

import android.content.Context

object AppPrefs {
    const val DEFAULT_REMOTE_CODE = 55555
    const val REMOTE_CODE_MIN = 49152
    const val REMOTE_CODE_MAX = 65535

    fun getRemoteCode(context: Context): Int = StageManagerState.readRemoteCode(context)

    fun setRemoteCode(context: Context, code: Int) {
        StageManagerState.writeRemoteCode(context, code)
    }

    fun getRemotePort(context: Context): Int = getRemoteCode(context)

    fun getRemotePin(context: Context): String = getRemoteCode(context).toString()

    fun isValidRemoteCode(text: String): Boolean {
        if (text.length != 5 || !text.all { it.isDigit() }) return false
        val value = text.toIntOrNull() ?: return false
        return value in REMOTE_CODE_MIN..REMOTE_CODE_MAX
    }

    fun getLastPlaylistId(context: Context): Long? = StageManagerState.readLastPlaylistId(context)

    fun setLastPlaylistId(context: Context, playlistId: Long) {
        StageManagerState.writeLastPlaylistId(context, playlistId)
    }
}
