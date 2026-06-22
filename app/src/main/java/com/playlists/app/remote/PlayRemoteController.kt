package com.playlists.app.remote

import android.content.Context
import com.playlists.app.PlaylistsApp
import com.playlists.app.data.FileType
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.data.Song
import com.playlists.app.ui.PdfHelper
import com.playlists.app.util.AppPrefs
import com.playlists.app.util.FileStorage
import com.playlists.app.util.SongTitles
import fi.iki.elonen.NanoHTTPD
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

object PlayRemoteController {
    private var server: PlayRemoteServer? = null
    private var publicUrl: String? = null
    private var appContext: Context? = null
    var activePlaylistId: Long? = null
        private set

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

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
        appContext = context.applicationContext
        val songs = entriesToRemoteSongs(entries)
        val html = context.assets.open("remote/play.html").bufferedReader().readText()
        val editHtml = context.assets.open("remote/edit.html").bufferedReader().readText()
        val pinHtml = context.assets.open("remote/pin.html").bufferedReader().readText()
        val port = AppPrefs.getRemotePort(context)
        val pin = AppPrefs.getRemotePin(context)
        val remote = PlayRemoteServer(
            hostname = "127.0.0.1",
            port = port,
            pin = pin,
            playlistName = playlistName,
            songs = songs,
            html = html,
            editHtml = editHtml,
            pinHtml = pinHtml,
            onStopRequested = { stop() },
            onUpload = { title, key, notes, tempFile, mimeType ->
                runBlocking {
                    handleUpload(playlistId, title, key, notes, tempFile, mimeType)
                }
            },
            onReorder = { entryIds ->
                runBlocking { mutatePlaylist(playlistId) { app -> app.playlistRepository.reorder(playlistId, entryIds) } }
            },
            onRemove = { entryId ->
                runBlocking { mutatePlaylist(playlistId) { app -> app.playlistRepository.removeSong(entryId) } }
            },
            onAdd = { songId ->
                runBlocking { mutatePlaylist(playlistId) { app -> app.playlistRepository.addSong(playlistId, songId) } }
            },
            onSearchSongs = { query ->
                runBlocking { searchSongs(query) }
            },
        )
        return try {
            remote.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            val listeningPort = remote.listeningPort
            if (listeningPort <= 0) {
                remote.stop()
                return Result.failure(
                    IllegalStateException("Could not bind port $port — try another in Settings"),
                )
            }
            val tunnelResult = CloudflareTunnel.start(context.applicationContext, listeningPort)
            if (tunnelResult.isFailure) {
                remote.stop()
                return Result.failure(
                    tunnelResult.exceptionOrNull()
                        ?: IllegalStateException("Cloudflare tunnel failed"),
                )
            }
            val tunnelUrl = tunnelResult.getOrThrow()
            server = remote
            activePlaylistId = playlistId
            publicUrl = "$tunnelUrl/"
            _running.value = true
            RemotePlayService.start(context.applicationContext, playlistName)
            Result.success(publicUrl!!)
        } catch (e: Exception) {
            CloudflareTunnel.stop()
            remote.stop()
            Result.failure(e)
        }
    }

    fun refreshSongs(entries: List<PlaylistSongWithDetails>) {
        server?.replaceSongs(entriesToRemoteSongs(entries))
    }

    fun stop() {
        appContext?.let { RemotePlayService.stop(it) }
        CloudflareTunnel.stop()
        server?.stop()
        server = null
        publicUrl = null
        activePlaylistId = null
        appContext = null
        _running.value = false
    }

    private fun entriesToRemoteSongs(entries: List<PlaylistSongWithDetails>): List<PlayRemoteServer.RemoteSong> =
        entries.map { entry ->
            val file = File(entry.filePath)
            val fileType = FileType.valueOf(entry.fileType)
            val pageCount = if (file.exists()) {
                PdfHelper.pageCount(file, fileType)
            } else {
                1
            }
            PlayRemoteServer.RemoteSong(
                entryId = entry.id,
                songId = entry.songId,
                title = entry.title,
                keySignature = entry.keySignature,
                notes = entry.notes,
                fileType = entry.fileType,
                filePath = entry.filePath,
                pageCount = pageCount.coerceAtLeast(1),
                isDeleted = entry.isDeleted,
            )
        }

    private suspend fun mutatePlaylist(
        playlistId: Long,
        block: suspend (PlaylistsApp) -> Unit,
    ): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("Server not ready"))
        val app = PlaylistsApp.from(ctx as android.app.Application)
        return try {
            block(app)
            val entries = app.playlistRepository.getSongs(playlistId)
            if (entries.isEmpty()) {
                return Result.failure(IllegalStateException("Playlist is empty"))
            }
            server?.replaceSongs(entriesToRemoteSongs(entries))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun searchSongs(query: String): List<PlayRemoteServer.SearchSong> {
        val ctx = appContext ?: return emptyList()
        val app = PlaylistsApp.from(ctx as android.app.Application)
        return app.songRepository.search(query).map { song ->
            PlayRemoteServer.SearchSong(
                id = song.id,
                title = song.title,
                keySignature = song.keySignature,
                notes = song.notes,
            )
        }
    }

    private suspend fun handleUpload(
        playlistId: Long,
        title: String,
        key: String,
        notes: String,
        tempFile: File,
        mimeType: String,
    ): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("Server not ready"))
        val app = PlaylistsApp.from(ctx as android.app.Application)
        return try {
            val ext = FileStorage.extensionForMime(mimeType)
            val stored = FileStorage.storeBytes(ctx, tempFile.readBytes(), ext)
            val fileType = if (mimeType.contains("pdf")) FileType.PDF else FileType.IMAGE
            val songId = app.songRepository.insert(
                Song(
                    title = title.trim().ifBlank { SongTitles.fromFilename(tempFile.name) }
                        .ifBlank { "Uploaded song" },
                    keySignature = key.trim(),
                    notes = notes.trim(),
                    filePath = stored.absolutePath,
                    fileType = fileType.name,
                    mimeType = mimeType,
                ),
            )
            app.playlistRepository.addSong(playlistId, songId)
            val entries = app.playlistRepository.getSongs(playlistId)
            server?.let { remote ->
                remote.replaceSongs(entriesToRemoteSongs(entries))
                remote.goToSong(entries.lastIndex)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
