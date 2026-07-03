package com.playlists.app.remote

import android.content.Context
import com.playlists.app.R
import com.playlists.app.util.AppPrefs

data class RemotePlayUrlEntry(
    val label: String,
    val url: String,
)

object RemotePlayUrls {

    fun playlistSuffix(playlistId: Long?): String =
        if (playlistId != null) "/?playlist=$playlistId" else "/"

    internal fun shouldShowStableUrl(
        mode: RemotePlayMode,
        stableConfigured: Boolean,
        stableUrlActive: Boolean,
    ): Boolean = stableConfigured && (mode != RemotePlayMode.STABLE || stableUrlActive)

    fun forSession(
        context: Context,
        session: PlayRemoteController.SessionSnapshot,
        playlistId: Long?,
    ): List<RemotePlayUrlEntry> {
        val suffix = playlistSuffix(playlistId)
        val entries = mutableListOf<RemotePlayUrlEntry>()

        val stableBase = AppPrefs.buildStableRedirectBase(context)
        if (shouldShowStableUrl(session.mode, stableBase != null, session.stableUrlActive)) {
            entries.add(
                RemotePlayUrlEntry(
                    label = context.getString(R.string.remote_url_label_stable),
                    url = "$stableBase$suffix",
                ),
            )
        }

        session.tunnelBaseUrl?.let { tunnel ->
            entries.add(
                RemotePlayUrlEntry(
                    label = context.getString(R.string.remote_url_label_cloudflare),
                    url = "$tunnel$suffix",
                ),
            )
        }

        addLanEntries(context, entries, session.localPort, suffix)
        return entries
    }

    /** All URLs for the status dialog — uses live session or best-effort fallbacks. */
    fun collect(context: Context, playlistId: Long?): List<RemotePlayUrlEntry> {
        PlayRemoteController.sessionSnapshot()?.let { session ->
            return forSession(context, session, playlistId)
        }

        val suffix = playlistSuffix(playlistId)
        val port = AppPrefs.getRemotePort(context)
        val entries = mutableListOf<RemotePlayUrlEntry>()
        val stableBase = AppPrefs.buildStableRedirectBase(context)

        stableBase?.let { base ->
            entries.add(
                RemotePlayUrlEntry(
                    label = context.getString(R.string.remote_url_label_stable),
                    url = "$base$suffix",
                ),
            )
        }

        PlayRemoteController.displayUrl()?.let { publicUrl ->
            if (publicUrl.contains("trycloudflare.com")) {
                val tunnelBase = publicUrl.substringBefore('?').trimEnd('/')
                if (entries.none { it.url.startsWith(tunnelBase) }) {
                    entries.add(
                        RemotePlayUrlEntry(
                            label = context.getString(R.string.remote_url_label_cloudflare),
                            url = "$tunnelBase$suffix",
                        ),
                    )
                }
            }
        }

        addLanEntries(context, entries, port, suffix)

        if (entries.isEmpty()) {
            PlayRemoteController.displayUrl()?.let { publicUrl ->
                entries.add(
                    RemotePlayUrlEntry(
                        label = context.getString(R.string.remote_url_label_cloudflare),
                        url = publicUrl,
                    ),
                )
            }
        }

        return entries
    }

    private fun addLanEntries(
        context: Context,
        entries: MutableList<RemotePlayUrlEntry>,
        port: Int,
        suffix: String,
    ) {
        val lanAddresses = NetworkAddresses.localLanIpv4Addresses()
        lanAddresses.forEachIndexed { index, lan ->
            val label = lanLabel(context, lan, index, lanAddresses.size)
            entries.add(
                RemotePlayUrlEntry(
                    label = label,
                    url = "http://${lan.address}:$port$suffix",
                ),
            )
        }
    }

    private fun lanLabel(
        context: Context,
        lan: NetworkAddresses.LanIpv4,
        index: Int,
        total: Int,
    ): String {
        if (lan.isLikelyHotspot) {
            return context.getString(R.string.remote_url_label_lan_hotspot, index + 1)
        }
        if (total == 1) {
            return context.getString(R.string.remote_url_label_lan)
        }
        return context.getString(R.string.remote_url_label_lan_numbered, index + 1)
    }
}
