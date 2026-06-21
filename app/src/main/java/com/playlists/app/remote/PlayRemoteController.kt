package com.playlists.app.remote

import android.content.Context
import com.playlists.app.data.FileType
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.ui.PdfHelper
import fi.iki.elonen.NanoHTTPD
import java.io.File

object PlayRemoteController {
    private var server: PlayRemoteServer? = null
    private var publicUrl: String? = null
    var activePlaylistId: Long? = null
        private set

    fun isRunning(): Boolean = server?.isAlive == true

    fun isRunningFor(playlistId: Long): Boolean =
        isRunning() && activePlaylistId == playlistId

    fun currentUrl(): String? = if (isRunning()) publicUrl else null

    fun start(
        context: Context,
        playlistId: Long,
        playlistName: String,
        entries: List<PlaylistSongWithDetails>,
    ): Result<String> {
        stop()
        if (entries.isEmpty()) {
            return Result.failure(IllegalStateException("Playlist is empty"))
        }
        val ip = NetworkAddresses.localLanIp()
            ?: return Result.failure(IllegalStateException("No LAN IP found — connect to Wi‑Fi"))
        val songs = entries.map { entry ->
            val file = File(entry.filePath)
            val fileType = FileType.valueOf(entry.fileType)
            val pageCount = if (file.exists()) {
                PdfHelper.pageCount(file, fileType)
            } else {
                1
            }
            PlayRemoteServer.RemoteSong(
                title = entry.title,
                fileType = entry.fileType,
                filePath = entry.filePath,
                pageCount = pageCount.coerceAtLeast(1),
            )
        }
        val html = context.assets.open("remote/play.html").bufferedReader().readText()
        val remote = PlayRemoteServer(0, playlistName, songs, html)
        return try {
            remote.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            val port = remote.listeningPort
            if (port <= 0) {
                remote.stop()
                return Result.failure(IllegalStateException("Could not bind port"))
            }
            server = remote
            activePlaylistId = playlistId
            publicUrl = "http://$ip:$port/"
            Result.success(publicUrl!!)
        } catch (e: Exception) {
            remote.stop()
            Result.failure(e)
        }
    }

    fun stop() {
        server?.stop()
        server = null
        publicUrl = null
        activePlaylistId = null
    }
}
