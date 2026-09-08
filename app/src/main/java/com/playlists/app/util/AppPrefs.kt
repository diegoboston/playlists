package com.playlists.app.util

import android.content.Context
import com.playlists.app.remote.TunnelRedirectClient

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

    private val WORKERS_SUBDOMAIN_PATTERN = Regex("""^[a-z0-9-]+$""")

    fun getTunnelRedirectSubdomain(context: Context): String? =
        StageManagerState.readTunnelRedirectSubdomain(context)

    fun getTunnelRedirectSecret(context: Context): String? =
        StageManagerState.readTunnelRedirectSecret(context)

    fun isTunnelRedirectConfigured(context: Context): Boolean =
        buildStableRedirectBase(context) != null && !getTunnelRedirectSecret(context).isNullOrBlank()

    fun isStableRedirectReady(context: Context): Boolean =
        isTunnelRedirectConfigured(context) && StageManagerState.readTunnelRedirectValidated(context)

    fun setStableRedirectValidated(context: Context, validated: Boolean) {
        StageManagerState.writeTunnelRedirectValidated(context, validated)
    }

    fun isValidWorkersSubdomain(text: String): Boolean {
        val trimmed = text.trim().lowercase()
        if (trimmed.isEmpty()) return true
        return WORKERS_SUBDOMAIN_PATTERN.matches(trimmed)
    }

    fun buildStableRedirectBase(context: Context): String? {
        val subdomain = getTunnelRedirectSubdomain(context)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: return null
        if (!WORKERS_SUBDOMAIN_PATTERN.matches(subdomain)) return null
        return TunnelRedirectClient.buildWorkerBaseUrl(subdomain)
    }

    fun setTunnelRedirect(context: Context, subdomain: String?, secret: String?) {
        val normalizedSubdomain = subdomain?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val normalizedSecret = secret?.trim()?.takeIf { it.isNotEmpty() }
        StageManagerState.writeTunnelRedirectSubdomain(context, normalizedSubdomain)
        StageManagerState.writeTunnelRedirectSecret(context, normalizedSecret)
    }

    fun isAdjustPageEnabled(context: Context): Boolean =
        StageManagerState.readAdjustPageEnabled(context)

    fun setAdjustPageEnabled(context: Context, enabled: Boolean) {
        StageManagerState.writeAdjustPageEnabled(context, enabled)
    }
}
