package com.playlists.app.util

import android.content.Context
import org.json.JSONObject
import java.io.File

object StageManagerState {
    private const val PREFS = "playlists_prefs"
    private const val KEY_REMOTE_CODE = "remote_code"
    private const val KEY_REMOTE_PORT = "remote_port"
    private const val KEY_LAST_PLAYLIST_ID = "last_playlist_id"

    fun readRemoteCode(context: Context): Int {
        readFromFile()?.let { json ->
            if (json.has(KEY_REMOTE_CODE)) {
                return json.getInt(KEY_REMOTE_CODE).coerceIn(AppPrefs.REMOTE_CODE_MIN, AppPrefs.REMOTE_CODE_MAX)
            }
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_REMOTE_CODE)) {
            return prefs.getInt(KEY_REMOTE_CODE, AppPrefs.DEFAULT_REMOTE_CODE)
                .coerceIn(AppPrefs.REMOTE_CODE_MIN, AppPrefs.REMOTE_CODE_MAX)
        }
        val legacyPort = prefs.getInt(KEY_REMOTE_PORT, AppPrefs.DEFAULT_REMOTE_CODE)
        if (legacyPort in AppPrefs.REMOTE_CODE_MIN..AppPrefs.REMOTE_CODE_MAX) {
            return legacyPort
        }
        return AppPrefs.DEFAULT_REMOTE_CODE
    }

    fun writeRemoteCode(context: Context, code: Int) {
        val json = readFromFile() ?: JSONObject()
        json.put(KEY_REMOTE_CODE, code)
        json.remove(KEY_REMOTE_PORT)
        writeToFile(json)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_REMOTE_CODE, code)
            .remove(KEY_REMOTE_PORT)
            .apply()
    }

    fun readLastPlaylistId(context: Context): Long? {
        readFromFile()?.let { json ->
            if (json.has(KEY_LAST_PLAYLIST_ID)) {
                val id = json.getLong(KEY_LAST_PLAYLIST_ID)
                return if (id >= 0) id else null
            }
        }
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_PLAYLIST_ID, -1L)
        return if (id >= 0) id else null
    }

    fun writeLastPlaylistId(context: Context, playlistId: Long) {
        val json = readFromFile() ?: JSONObject()
        json.put(KEY_LAST_PLAYLIST_ID, playlistId)
        writeToFile(json)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_PLAYLIST_ID, playlistId)
            .apply()
    }

    fun exportFromSharedPreferences(context: Context) {
        if (StageManagerStorage.stateFile().exists()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = JSONObject()
        if (prefs.contains(KEY_REMOTE_CODE)) {
            json.put(KEY_REMOTE_CODE, prefs.getInt(KEY_REMOTE_CODE, AppPrefs.DEFAULT_REMOTE_CODE))
        } else {
            val legacyPort = prefs.getInt(KEY_REMOTE_PORT, -1)
            if (legacyPort in AppPrefs.REMOTE_CODE_MIN..AppPrefs.REMOTE_CODE_MAX) {
                json.put(KEY_REMOTE_CODE, legacyPort)
            }
        }
        if (prefs.contains(KEY_LAST_PLAYLIST_ID)) {
            json.put(KEY_LAST_PLAYLIST_ID, prefs.getLong(KEY_LAST_PLAYLIST_ID, -1L))
        }
        if (json.length() > 0) {
            writeToFile(json)
        }
    }

    private fun readFromFile(): JSONObject? {
        val file = StageManagerStorage.stateFile()
        if (!file.isFile) return null
        return runCatching {
            JSONObject(file.readText())
        }.getOrNull()
    }

    private fun writeToFile(json: JSONObject) {
        StageManagerStorage.ensureDirectories()
        StageManagerStorage.stateFile().writeText(json.toString())
    }
}
