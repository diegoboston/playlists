package com.playlists.app.remote

import android.content.Context
import com.playlists.app.PlaylistsApp
import com.playlists.app.data.FileType
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.data.Song
import com.playlists.app.ui.PdfHelper
import com.playlists.app.util.AppPrefs
import com.playlists.app.util.FileStorage
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
    private val stopLock = Any()
    private var session: RemotePlaySession? = null
    var activePlaylistId: Long? = null
        private set

    private data class RemotePlaySession(
        val mode: RemotePlayMode,
        val localPort: Int,
        val tunnelBaseUrl: String?,
        val publicUrl: String,
        val startWarnings: List<String>,
    )

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun isRunning(): Boolean = server?.isAlive == true

    fun isRunningFor(playlistId: Long): Boolean =
        isRunning() && activePlaylistId == playlistId

    fun currentUrl(): String? = if (isRunning()) publicUrl else null

    fun collectDebugInfo(): RemotePlayDebugInfo? {
        val active = session ?: return null
        val localUrl = "http://127.0.0.1:${active.localPort}/"
        val localProbe = RemotePlayHealth.probeGet(localUrl, timeoutMs = 4_000)
        val tunnelProbe = active.tunnelBaseUrl?.let {
            RemotePlayHealth.probeTunnelWithRetries(it, attempts = 3, pauseMs = 1_000, timeoutMs = 5_000)
        }
        val warnings = active.startWarnings.toMutableList()
        if (active.mode == RemotePlayMode.CLOUDFLARE && !CloudflareTunnel.isRunning()) {
            warnings.add("cloudflared is not running — the public URL will not work.")
            CloudflareTunnel.lastExitCode()?.let { code ->
                warnings.add("cloudflared last exit code: $code")
            }
        }
        if (tunnelProbe != null && !tunnelProbe.ok) {
            warnings.add(
                "Tunnel URL did not respond (${tunnelProbe.detail}). " +
                    "Browser “unreachable” usually means the tunnel died or is still starting.",
            )
        }
        if (!localProbe.ok) {
            warnings.add("Local HTTP server did not respond (${localProbe.detail}).")
        }
        return RemotePlayDebugInfo(
            mode = active.mode,
            localPort = active.localPort,
            localUrl = localUrl,
            tunnelBaseUrl = active.tunnelBaseUrl,
            publicUrl = active.publicUrl,
            serverAlive = server?.isAlive == true,
            tunnelProcessAlive = CloudflareTunnel.isRunning(),
            tunnelExitCode = CloudflareTunnel.lastExitCode(),
            localProbe = localProbe,
            tunnelProbe = tunnelProbe,
            cloudflaredLog = CloudflareTunnel.recentLogs(),
            warnings = warnings,
            checkedAtMs = System.currentTimeMillis(),
        )
    }

    fun start(
        context: Context,
        playlistId: Long?,
        playlistName: String,
        entries: List<PlaylistSongWithDetails>,
        mode: RemotePlayMode = RemotePlayMode.CLOUDFLARE,
    ): Result<String> {
        stop()
        appContext = context.applicationContext
        val playHtml = context.assets.open("remote/play.html").bufferedReader().readText()
        val indexHtml = context.assets.open("remote/index.html").bufferedReader().readText()
        val editHtml = context.assets.open("remote/edit.html").bufferedReader().readText()
        val pinHtml = context.assets.open("remote/pin.html").bufferedReader().readText()
        val port = AppPrefs.getRemoteCode(context)
        val pin = AppPrefs.getRemotePin(context)
        val remote = PlayRemoteServer(
            hostname = if (mode == RemotePlayMode.LAN) "0.0.0.0" else "127.0.0.1",
            port = port,
            pin = pin,
            requirePin = mode == RemotePlayMode.CLOUDFLARE,
            playHtml = playHtml,
            indexHtml = indexHtml,
            editHtml = editHtml,
            pinHtml = pinHtml,
            onLoadPlaylist = { id ->
                runBlocking { loadPlaylist(id) }
            },
            onUpload = { id, title, key, notes, tempFile, mimeType ->
                runBlocking { handleUpload(id, title, key, notes, tempFile, mimeType) }
            },
            onReorder = { id, entryIds ->
                runBlocking { mutatePlaylist(id) { app -> app.playlistRepository.reorder(id, entryIds) } }
            },
            onRemove = { id, entryId ->
                runBlocking { mutatePlaylist(id) { app -> app.playlistRepository.removeSong(entryId) } }
            },
            onAdd = { id, songId ->
                runBlocking { mutatePlaylist(id) { app -> app.playlistRepository.addSong(id, songId) } }
            },
            onAddPlaceholder = { id, title, key, notes ->
                runBlocking { handleAddPlaceholder(id, title, key, notes) }
            },
            onSearchSongs = { query ->
                runBlocking { searchSongs(query) }
            },
            onListSongs = {
                runBlocking { listArchiveSongs() }
            },
            onUpdateSong = { songId, title, key, notes ->
                runBlocking { updateSongMetadata(songId, title, key, notes) }
            },
            onListPlaylists = {
                runBlocking { listPlaylists() }
            },
            onRenamePlaylist = { id, name ->
                runBlocking { renamePlaylist(id, name) }
            },
            onSetPlaylistColor = { id, colorArgb ->
                runBlocking { setPlaylistColor(id, colorArgb) }
            },
            onReorderPlaylists = { playlistIds ->
                runBlocking { reorderPlaylists(playlistIds) }
            },
            onCreatePlaylist = { name ->
                runBlocking { createPlaylist(name) }
            },
            onDeletePlaylist = { id ->
                runBlocking { deletePlaylist(id) }
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
            val tunnelUrl = when (mode) {
                RemotePlayMode.CLOUDFLARE -> {
                    val tunnelResult = CloudflareTunnel.start(context.applicationContext, listeningPort)
                    if (tunnelResult.isFailure) {
                        remote.stop()
                        return Result.failure(
                            tunnelResult.exceptionOrNull()
                                ?: IllegalStateException("Cloudflare tunnel failed"),
                        )
                    }
                    tunnelResult.getOrThrow()
                }
                RemotePlayMode.LAN -> {
                    val ip = NetworkAddresses.localLanIp()
                    if (ip == null) {
                        remote.stop()
                        return Result.failure(
                            IllegalStateException(
                                "No LAN IP found — connect to Wi‑Fi or choose Cloudflare tunnel",
                            ),
                        )
                    }
                    "http://$ip:$listeningPort"
                }
            }
            val startWarnings = mutableListOf<String>()
            if (mode == RemotePlayMode.CLOUDFLARE) {
                val tunnelProbe = RemotePlayHealth.probeTunnelWithRetries(tunnelUrl)
                if (!tunnelProbe.ok) {
                    startWarnings.add(
                        "Tunnel not reachable yet (${tunnelProbe.detail}). " +
                            "Wait a few seconds, tap Refresh in the dialog, then open the URL.",
                    )
                }
            }
            val resolvedPublicUrl = if (playlistId != null) {
                "$tunnelUrl/?playlist=$playlistId"
            } else {
                "$tunnelUrl/"
            }
            server = remote
            activePlaylistId = playlistId
            publicUrl = resolvedPublicUrl
            session = RemotePlaySession(
                mode = mode,
                localPort = listeningPort,
                tunnelBaseUrl = if (mode == RemotePlayMode.CLOUDFLARE) tunnelUrl else null,
                publicUrl = resolvedPublicUrl,
                startWarnings = startWarnings,
            )
            _running.value = true
            RemotePlayService.start(context.applicationContext, playlistName)
            Result.success(resolvedPublicUrl)
        } catch (e: Exception) {
            CloudflareTunnel.stop()
            remote.stop()
            Result.failure(e)
        }
    }

    fun refreshSongs(entries: List<PlaylistSongWithDetails>) {
        val playlistId = activePlaylistId ?: return
        server?.reconcilePlayback(playlistId, entriesToRemoteSongs(entries))
    }

    fun stop() {
        val serviceContext: Context?
        synchronized(stopLock) {
            if (!_running.value && server == null) return
            serviceContext = appContext
        }
        teardownResources()
        serviceContext?.let { RemotePlayService.requestStop(it) }
    }

    internal fun teardownResources() {
        val remote: PlayRemoteServer?
        synchronized(stopLock) {
            if (!_running.value && server == null) return
            remote = server
            server = null
            publicUrl = null
            activePlaylistId = null
            appContext = null
            session = null
            _running.value = false
        }
        try {
            CloudflareTunnel.stop()
            remote?.stop()
        } catch (_: Exception) {
        }
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
                isPlaceholder = entry.isPlaceholder,
            )
        }

    private suspend fun loadPlaylist(id: Long): PlayRemoteServer.PlaylistLoad? {
        val ctx = appContext ?: return null
        val app = PlaylistsApp.from(ctx as android.app.Application)
        val playlist = app.playlistRepository.getById(id) ?: return null
        val entries = app.playlistRepository.getSongs(id)
        return PlayRemoteServer.PlaylistLoad(
            playlistId = id,
            playlistName = playlist.name,
            songs = entriesToRemoteSongs(entries),
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
            server?.reconcilePlayback(playlistId, entriesToRemoteSongs(entries))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun refreshActivePlaylistSongs() {
        val playlistId = activePlaylistId ?: return
        val ctx = appContext ?: return
        val app = PlaylistsApp.from(ctx as android.app.Application)
        val entries = app.playlistRepository.getSongs(playlistId)
        server?.reconcilePlayback(playlistId, entriesToRemoteSongs(entries))
    }

    private suspend fun listArchiveSongs(): List<PlayRemoteServer.ArchiveSong> {
        val ctx = appContext ?: return emptyList()
        val app = PlaylistsApp.from(ctx as android.app.Application)
        return app.songRepository.getAll().map { song ->
            PlayRemoteServer.ArchiveSong(
                id = song.id,
                title = song.title,
                keySignature = song.keySignature,
                notes = song.notes,
                fileType = song.fileType,
                isDeleted = song.deletedAt != null,
                isPlaceholder = song.isPlaceholder,
            )
        }
    }

    private suspend fun updateSongMetadata(
        songId: Long,
        title: String,
        key: String,
        notes: String,
    ): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("Server not ready"))
        val app = PlaylistsApp.from(ctx as android.app.Application)
        return try {
            val song = app.songRepository.getById(songId)
                ?: return Result.failure(IllegalStateException("Song not found"))
            app.songRepository.update(
                song.copy(
                    title = title.trim().ifBlank { song.title },
                    keySignature = key.trim(),
                    notes = notes.trim(),
                ),
            )
            refreshActivePlaylistSongs()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun listPlaylists(): List<PlayRemoteServer.RemotePlaylistSummary> {
        val ctx = appContext ?: return emptyList()
        val app = PlaylistsApp.from(ctx as android.app.Application)
        return app.playlistRepository.getAll().map { playlist ->
            PlayRemoteServer.RemotePlaylistSummary(
                id = playlist.id,
                name = playlist.name,
                colorArgb = playlist.colorArgb,
                songCount = app.playlistRepository.getSongs(playlist.id).size,
            )
        }
    }

    private suspend fun renamePlaylist(id: Long, name: String): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("Server not ready"))
        val app = PlaylistsApp.from(ctx as android.app.Application)
        return try {
            app.playlistRepository.rename(id, name)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun setPlaylistColor(id: Long, colorArgb: Int?): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("Server not ready"))
        val app = PlaylistsApp.from(ctx as android.app.Application)
        return try {
            app.playlistRepository.setColor(id, colorArgb)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun reorderPlaylists(playlistIds: List<Long>): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("Server not ready"))
        val app = PlaylistsApp.from(ctx as android.app.Application)
        return try {
            app.playlistRepository.reorder(playlistIds)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createPlaylist(name: String): Result<Long> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("Server not ready"))
        val app = PlaylistsApp.from(ctx as android.app.Application)
        return try {
            Result.success(app.playlistRepository.create(name))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun deletePlaylist(id: Long): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("Server not ready"))
        val app = PlaylistsApp.from(ctx as android.app.Application)
        return try {
            app.playlistRepository.delete(id)
            server?.clearPlayback(id)
            if (activePlaylistId == id) {
                activePlaylistId = null
            }
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
                isPlaceholder = song.isPlaceholder,
            )
        }
    }

    private suspend fun handleAddPlaceholder(
        playlistId: Long,
        title: String,
        key: String,
        notes: String,
    ): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("Server not ready"))
        val app = PlaylistsApp.from(ctx as android.app.Application)
        return try {
            val parsed = com.playlists.app.util.SongTitleMigration.parse(
                title,
                existingKey = key,
                existingNotes = notes,
            )
            val songId = app.songRepository.createPlaceholder(
                context = ctx,
                title = parsed.title,
                keySignature = parsed.keySignature,
                notes = parsed.notes,
            )
            app.playlistRepository.addSong(playlistId, songId)
            val entries = app.playlistRepository.getSongs(playlistId)
            server?.let { remote ->
                remote.reconcilePlayback(playlistId, entriesToRemoteSongs(entries))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
                    title = title.trim().ifBlank { "Uploaded song" },
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
                remote.reconcilePlayback(playlistId, entriesToRemoteSongs(entries))
                remote.goToSong(playlistId, entries.lastIndex)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
