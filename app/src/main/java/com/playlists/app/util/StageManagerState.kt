package com.playlists.app.util

import android.content.Context
import org.json.JSONObject
import java.io.File

object StageManagerState {
    private const val PREFS = "playlists_prefs"
    private const val KEY_REMOTE_CODE = "remote_code"
    private const val KEY_REMOTE_PORT = "remote_port"
    private const val KEY_LAST_PLAYLIST_ID = "last_playlist_id"
    private const val KEY_TUNNEL_REDIRECT_SUBDOMAIN = "tunnel_redirect_subdomain"
    private const val KEY_TUNNEL_REDIRECT_SECRET = "tunnel_redirect_secret"
    private const val KEY_TUNNEL_REDIRECT_VALIDATED = "tunnel_redirect_validated"

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

    fun readTunnelRedirectSubdomain(context: Context): String? {
        readFromFile()?.let { json ->
            if (json.has(KEY_TUNNEL_REDIRECT_SUBDOMAIN)) {
                return json.optString(KEY_TUNNEL_REDIRECT_SUBDOMAIN).takeIf { it.isNotBlank() }
            }
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TUNNEL_REDIRECT_SUBDOMAIN, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun writeTunnelRedirectSubdomain(context: Context, subdomain: String?) {
        val json = readFromFile() ?: JSONObject()
        val trimmed = subdomain?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed == null) {
            json.remove(KEY_TUNNEL_REDIRECT_SUBDOMAIN)
        } else {
            json.put(KEY_TUNNEL_REDIRECT_SUBDOMAIN, trimmed)
        }
        writeToFile(json)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (trimmed == null) {
                    remove(KEY_TUNNEL_REDIRECT_SUBDOMAIN)
                } else {
                    putString(KEY_TUNNEL_REDIRECT_SUBDOMAIN, trimmed)
                }
            }
            .apply()
    }

    fun readTunnelRedirectSecret(context: Context): String? {
        readFromFile()?.let { json ->
            if (json.has(KEY_TUNNEL_REDIRECT_SECRET)) {
                return json.optString(KEY_TUNNEL_REDIRECT_SECRET).takeIf { it.isNotBlank() }
            }
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TUNNEL_REDIRECT_SECRET, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun readTunnelRedirectValidated(context: Context): Boolean {
        readFromFile()?.let { json ->
            if (json.has(KEY_TUNNEL_REDIRECT_VALIDATED)) {
                return json.getBoolean(KEY_TUNNEL_REDIRECT_VALIDATED)
            }
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_TUNNEL_REDIRECT_VALIDATED, false)
    }

    fun writeTunnelRedirectValidated(context: Context, validated: Boolean) {
        val json = readFromFile() ?: JSONObject()
        json.put(KEY_TUNNEL_REDIRECT_VALIDATED, validated)
        writeToFile(json)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TUNNEL_REDIRECT_VALIDATED, validated)
            .apply()
    }

    fun writeTunnelRedirectSecret(context: Context, secret: String?) {
        val json = readFromFile() ?: JSONObject()
        val trimmed = secret?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed == null) {
            json.remove(KEY_TUNNEL_REDIRECT_SECRET)
        } else {
            json.put(KEY_TUNNEL_REDIRECT_SECRET, trimmed)
        }
        writeToFile(json)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (trimmed == null) {
                    remove(KEY_TUNNEL_REDIRECT_SECRET)
                } else {
                    putString(KEY_TUNNEL_REDIRECT_SECRET, trimmed)
                }
            }
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
