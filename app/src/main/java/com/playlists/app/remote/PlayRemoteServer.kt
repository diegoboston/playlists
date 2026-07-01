package com.playlists.app.remote

import android.graphics.Bitmap
import com.playlists.app.ui.PdfHelper
import com.playlists.app.util.SongFileNames
import com.playlists.app.util.SongTitleMigration
import com.playlists.app.util.SongStoragePaths
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayRemoteServer(
    hostname: String,
    port: Int,
    private val pin: String,
    private val requirePin: Boolean = true,
    private val playHtml: String,
    private val indexHtml: String,
    private val editHtml: String,
    private val songsHtml: String,
    private val pinHtml: String,
    private val songDisplayJs: String,
    private val compatJs: String,
    private val uploadJs: String,
    private val uploadPanelCss: String,
    private val onLoadPlaylist: ((playlistId: Long) -> PlaylistLoad?)? = null,
    private val onUploadSong: ((title: String, key: String, notes: String, tempFile: File, mimeType: String) -> Result<Unit>)? = null,
    private val onUpload: ((playlistId: Long, title: String, key: String, notes: String, tempFile: File, mimeType: String) -> Result<Unit>)? = null,
    private val onReorder: ((playlistId: Long, entryIds: List<Long>) -> Result<Unit>)? = null,
    private val onRemove: ((playlistId: Long, entryId: Long) -> Result<Unit>)? = null,
    private val onAdd: ((playlistId: Long, songId: Long) -> Result<Unit>)? = null,
    private val onAddPlaceholder: ((playlistId: Long, title: String, key: String, notes: String) -> Result<Unit>)? = null,
    private val onSearchSongs: ((query: String) -> List<SearchSong>)? = null,
    private val onListSongs: (() -> List<ArchiveSong>)? = null,
    private val onGetSongSortState: (() -> SongSortJson)? = null,
    private val onSortSongs: ((criterion: String) -> Result<Unit>)? = null,
    private val onUpdateSong: ((songId: Long, title: String, key: String, notes: String) -> Result<Unit>)? = null,
    private val onListPlaylists: (() -> List<RemotePlaylistSummary>)? = null,
    private val onRenamePlaylist: ((id: Long, name: String) -> Result<Unit>)? = null,
    private val onSetPlaylistColor: ((id: Long, colorArgb: Int?) -> Result<Unit>)? = null,
    private val onReorderPlaylists: ((playlistIds: List<Long>) -> Result<Unit>)? = null,
    private val onCreatePlaylist: ((name: String) -> Result<Long>)? = null,
    private val onDeletePlaylist: ((id: Long) -> Result<Unit>)? = null,
    private val onMatchQuickstart: ((text: String) -> List<QuickstartMatchJson>)? = null,
    private val onCreateQuickstart: ((name: String, text: String, withPlaceholders: Boolean) -> Result<Long>)? = null,
) : NanoHTTPD(hostname, port) {

    private val sessionToken: String = UUID.randomUUID().toString()
    private val playbackStates = ConcurrentHashMap<Long, PlaybackState>()

    data class RemoteSong(
        val entryId: Long,
        val songId: Long,
        val title: String,
        val keySignature: String,
        val notes: String,
        val fileType: String,
        val filePath: String,
        val pageCount: Int,
    )

    data class PlaylistLoad(
        val playlistId: Long,
        val playlistName: String,
        val songs: List<RemoteSong>,
    )

    data class SearchSong(
        val id: Long,
        val title: String,
        val keySignature: String,
        val notes: String,
    )

    data class ArchiveSong(
        val id: Long,
        val title: String,
        val keySignature: String,
        val notes: String,
        val fileType: String,
    )

    data class SongSortJson(
        val criterion: String,
        val reversed: Boolean,
    )

    data class RemotePlaylistSummary(
        val id: Long,
        val name: String,
        val colorArgb: Int?,
        val songCount: Int,
    )

    data class QuickstartMatchJson(
        val line: String,
        val songId: Long?,
        val title: String?,
        val key: String?,
    )

    private data class PlaybackState(
        var songIndex: Int = 0,
        var pageIndex: Int = 0,
    )

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.substringBefore('?')
        if (uri == "/api/auth" && session.method == Method.POST) {
            return handleAuth(session)
        }
        if (requirePin && !isAuthorized(session)) {
            return when {
                uri == "/compat.js" -> jsResponse(compatJs)
                uri == "/song-display.js" -> jsResponse(songDisplayJs)
                uri == "/upload.js" -> jsResponse(uploadJs)
                uri == "/upload-panel.css" -> cssResponse(uploadPanelCss)
                isHtmlPageRoute(uri) -> htmlResponse(pinHtml)
                uri.startsWith("/api/") -> jsonUnauthorized()
                else -> notFound()
            }
        }
        matchPlaylistRoute(uri)?.let { (playlistId, sub) ->
            return when {
                sub == "/state" -> handleState(playlistId)
                sub == "/entries" -> handleEntries(playlistId)
                sub == "/navigate" && session.method == Method.POST -> handleNavigate(playlistId, session)
                sub == "/media" -> serveMedia(playlistId, session)
                sub == "/download" -> serveDownload(playlistId, session)
                sub == "/reorder" && session.method == Method.POST -> handleReorder(playlistId, session)
                sub == "/remove" && session.method == Method.POST -> handleRemove(playlistId, session)
                sub == "/add" && session.method == Method.POST -> handleAdd(playlistId, session)
                sub == "/add-placeholder" && session.method == Method.POST ->
                    handleAddPlaceholder(playlistId, session)
                sub == "/upload" && session.method == Method.POST -> handleUpload(playlistId, session)
                sub == "/rename" && session.method == Method.POST -> handleRenamePlaylist(playlistId, session)
                sub == "/color" && session.method == Method.POST -> handleSetPlaylistColor(playlistId, session)
                sub == "/delete" && session.method == Method.POST -> handleDeletePlaylist(playlistId)
                else -> notFound()
            }
        }
        return when {
            isPlayPageRoute(uri, session) -> htmlResponse(playHtml)
            uri == "/" || uri == "/index.html" -> htmlResponse(indexHtml)
            uri == "/edit" || uri == "/edit.html" -> htmlResponse(editHtml)
            uri == "/songs" || uri == "/songs.html" -> htmlResponse(songsHtml)
            uri == "/song-display.js" -> jsResponse(songDisplayJs)
            uri == "/compat.js" -> jsResponse(compatJs)
            uri == "/upload.js" -> jsResponse(uploadJs)
            uri == "/upload-panel.css" -> cssResponse(uploadPanelCss)
            uri == "/api/songs" && session.method == Method.GET -> handleListSongs()
            uri == "/api/songs/sort" && session.method == Method.POST -> handleSortSongs(session)
            uri == "/api/songs/upload" && session.method == Method.POST -> handleCatalogUpload(session)
            uri == "/api/songs/update" && session.method == Method.POST -> handleUpdateSong(session)
            uri == "/api/playlists" && session.method == Method.GET -> handleListPlaylists()
            uri == "/api/playlists/reorder" && session.method == Method.POST -> handleReorderPlaylists(session)
            uri == "/api/playlists/create" && session.method == Method.POST -> handleCreatePlaylist(session)
            uri == "/api/playlists/quickstart/match" && session.method == Method.POST ->
                handleMatchQuickstart(session)
            uri == "/api/playlists/quickstart/create" && session.method == Method.POST ->
                handleCreateQuickstart(session)
            uri == "/api/parse-filename" -> handleParseFilename(session)
            uri == "/api/songs/search" -> handleSearch(session)
            else -> notFound()
        }
    }

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not found")

    private fun isHtmlPageRoute(uri: String): Boolean =
        uri == "/" || uri == "/index.html" ||
            uri == "/play" || uri == "/play.html" ||
            uri == "/edit" || uri == "/edit.html" ||
            uri == "/songs" || uri == "/songs.html"

    private data class ParsedUpload(
        val title: String,
        val key: String,
        val notes: String,
        val tempFile: File,
        val mimeType: String,
    )

    private fun isPlayPageRoute(uri: String, session: IHTTPSession): Boolean =
        uri == "/play" || uri == "/play.html" ||
            ((uri == "/" || uri == "/index.html") && hasPlaylistQuery(session))

    private fun hasPlaylistQuery(session: IHTTPSession): Boolean {
        val id = session.parameters["playlist"]?.firstOrNull() ?: return false
        return id.matches(Regex("\\d+"))
    }

    private fun matchPlaylistRoute(uri: String): Pair<Long, String>? {
        val match = Regex("""^/api/playlists/(\d+)(/.*)?$""").matchEntire(uri) ?: return null
        val playlistId = match.groupValues[1].toLongOrNull() ?: return null
        val sub = match.groupValues[2].ifEmpty { "/" }
        return playlistId to sub
    }

    private fun loadPlaylist(playlistId: Long): PlaylistLoad? = onLoadPlaylist?.invoke(playlistId)

    private fun playback(playlistId: Long): PlaybackState =
        playbackStates.getOrPut(playlistId) { PlaybackState() }

    fun reconcilePlayback(playlistId: Long, songs: List<RemoteSong>) {
        val state = playback(playlistId)
        synchronized(state) {
            if (songs.isEmpty()) {
                state.songIndex = 0
                state.pageIndex = 0
                return
            }
            state.songIndex = state.songIndex.coerceIn(0, songs.lastIndex)
            val pages = songs[state.songIndex].pageCount.coerceAtLeast(1)
            state.pageIndex = state.pageIndex.coerceIn(0, pages - 1)
        }
    }

    fun goToSong(playlistId: Long, index: Int) {
        val state = playback(playlistId)
        synchronized(state) {
            state.songIndex = index.coerceAtLeast(0)
            state.pageIndex = 0
        }
    }

    fun clearPlayback(playlistId: Long) {
        playbackStates.remove(playlistId)
    }

    private fun handleState(playlistId: Long): Response {
        val loaded = loadPlaylist(playlistId) ?: return jsonError("Playlist not found")
        val state = playback(playlistId)
        clampPlayback(loaded.songs, state)
        return jsonResponse(buildStateJson(loaded, state))
    }

    private fun handleEntries(playlistId: Long): Response {
        val loaded = loadPlaylist(playlistId) ?: return jsonError("Playlist not found")
        val state = playback(playlistId)
        return jsonResponse(buildEntriesJson(loaded, state))
    }

    private fun handleNavigate(playlistId: Long, session: IHTTPSession): Response {
        val loaded = loadPlaylist(playlistId) ?: return jsonError("Playlist not found")
        val state = playback(playlistId)
        val raw = readPostBody(session)
        val direction = Regex(""""direction"\s*:\s*"(\w+)"""").find(raw)?.groupValues?.get(1)
        when (direction) {
            "next" -> step(loaded.songs, state, 1)
            "prev" -> step(loaded.songs, state, -1)
            "reset" -> resetPlayback(state)
        }
        return jsonResponse(buildStateJson(loaded, state))
    }

    private fun handleReorder(playlistId: Long, session: IHTTPSession): Response {
        val handler = onReorder ?: return jsonError("Reorder not available")
        val entryIds = parseLongArray(readPostBody(session), "entryIds")
            ?: return jsonError("Missing entryIds")
        if (entryIds.isEmpty()) return jsonError("Empty playlist")
        return if (handler(playlistId, entryIds).isSuccess) {
            handleEntries(playlistId)
        } else {
            jsonError("Reorder failed")
        }
    }

    private fun handleRemove(playlistId: Long, session: IHTTPSession): Response {
        val handler = onRemove ?: return jsonError("Remove not available")
        val entryId = parseLongField(readPostBody(session), "entryId")
            ?: return jsonError("Missing entryId")
        return if (handler(playlistId, entryId).isSuccess) {
            handleEntries(playlistId)
        } else {
            jsonError("Remove failed")
        }
    }

    private fun handleAdd(playlistId: Long, session: IHTTPSession): Response {
        val handler = onAdd ?: return jsonError("Add not available")
        val songId = parseLongField(readPostBody(session), "songId")
            ?: return jsonError("Missing songId")
        return if (handler(playlistId, songId).isSuccess) {
            handleEntries(playlistId)
        } else {
            jsonError("Add failed")
        }
    }

    private fun handleAddPlaceholder(playlistId: Long, session: IHTTPSession): Response {
        val handler = onAddPlaceholder ?: return jsonError("Add placeholder not available")
        val raw = readPostBody(session)
        val title = parseStringField(raw, "title") ?: return jsonError("Missing title")
        if (title.isBlank()) return jsonError("Missing title")
        val key = parseStringField(raw, "key").orEmpty()
        val notes = parseStringField(raw, "notes").orEmpty()
        return if (handler(playlistId, title, key, notes).isSuccess) {
            handleEntries(playlistId)
        } else {
            jsonError("Add placeholder failed")
        }
    }

    private fun handleSearch(session: IHTTPSession): Response {
        val handler = onSearchSongs ?: return jsonError("Search not available")
        val query = session.parameters["q"]?.firstOrNull().orEmpty()
        val results = handler(query)
        val sb = StringBuilder("""{"songs":[""")
        results.forEachIndexed { i, song ->
            if (i > 0) sb.append(',')
            sb.append(
                """{"id":${song.id},"title":${jsonStr(song.title)},"key":${jsonStr(song.keySignature)},"notes":${jsonStr(song.notes)}}""",
            )
        }
        sb.append("]}")
        return jsonResponse(sb.toString())
    }

    private fun handleListSongs(): Response {
        if (onListSongs == null) return jsonError("Song list not available")
        return jsonResponse(buildSongsResponseJson())
    }

    private fun buildSongsResponseJson(): String {
        val sort = onGetSongSortState?.invoke() ?: SongSortJson("alpha", false)
        return """{"sort":{"criterion":${jsonStr(sort.criterion)},"reversed":${sort.reversed}},"songs":[${buildSongsArrayJson()}]}"""
    }

    private fun buildSongsListJson(): String = """{"songs":[${buildSongsArrayJson()}]}"""

    private fun buildSongsArrayJson(): String {
        val handler = onListSongs ?: return ""
        val songs = handler()
        val sb = StringBuilder()
        songs.forEachIndexed { i, song ->
            if (i > 0) sb.append(',')
            sb.append(
                """{"id":${song.id},"title":${jsonStr(song.title)},"key":${jsonStr(song.keySignature)},"notes":${jsonStr(song.notes)},"fileType":${jsonStr(song.fileType)}}""",
            )
        }
        return sb.toString()
    }

    private fun handleSortSongs(session: IHTTPSession): Response {
        val handler = onSortSongs ?: return jsonError("Sort not available")
        val criterion = parseStringField(readPostBody(session), "criterion")
            ?: return jsonError("Missing criterion")
        val normalized = criterion.lowercase()
        if (normalized !in setOf("alpha", "added", "viewed")) {
            return jsonError("Invalid criterion")
        }
        return if (handler(normalized).isSuccess) {
            jsonResponse(buildSongsResponseJson())
        } else {
            jsonError("Sort failed")
        }
    }

    private fun handleUpdateSong(session: IHTTPSession): Response {
        val handler = onUpdateSong ?: return jsonError("Song update not available")
        val raw = readPostBody(session)
        val songId = parseLongField(raw, "songId") ?: return jsonError("Missing songId")
        val title = parseStringField(raw, "title") ?: return jsonError("Missing title")
        if (title.isBlank()) return jsonError("Missing title")
        val key = parseStringField(raw, "key").orEmpty()
        val notes = parseStringField(raw, "notes").orEmpty()
        return if (handler(songId, title, key, notes).isSuccess) {
            jsonResponse(buildSongsResponseJson())
        } else {
            jsonError("Song update failed")
        }
    }

    private fun handleListPlaylists(): Response {
        if (onListPlaylists == null) return jsonError("Playlist list not available")
        return jsonResponse(buildPlaylistsListJson())
    }

    private fun buildPlaylistsListJson(): String {
        val handler = onListPlaylists ?: return """{"playlists":[]}"""
        val playlists = handler()
        val sb = StringBuilder("""{"playlists":[""")
        playlists.forEachIndexed { i, playlist ->
            if (i > 0) sb.append(',')
            val colorJson = playlist.colorArgb?.toString() ?: "null"
            sb.append(
                """{"id":${playlist.id},"name":${jsonStr(playlist.name)},"color":$colorJson,"songCount":${playlist.songCount}}""",
            )
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun handleRenamePlaylist(playlistId: Long, session: IHTTPSession): Response {
        val handler = onRenamePlaylist ?: return jsonError("Playlist rename not available")
        val name = parseStringField(readPostBody(session), "name") ?: return jsonError("Missing name")
        if (name.isBlank()) return jsonError("Missing name")
        return if (handler(playlistId, name).isSuccess) {
            jsonResponse(buildPlaylistsListJson())
        } else {
            jsonError("Playlist rename failed")
        }
    }

    private fun handleSetPlaylistColor(playlistId: Long, session: IHTTPSession): Response {
        val handler = onSetPlaylistColor ?: return jsonError("Playlist color not available")
        val raw = readPostBody(session)
        if (!Regex(""""color"\s*:""").containsMatchIn(raw)) return jsonError("Missing color")
        val color = if (Regex(""""color"\s*:\s*null""").containsMatchIn(raw)) {
            null
        } else {
            Regex(""""color"\s*:\s*(-?\d+)""").find(raw)?.groupValues?.get(1)?.toIntOrNull()
                ?: return jsonError("Invalid color")
        }
        return if (handler(playlistId, color).isSuccess) {
            jsonResponse(buildPlaylistsListJson())
        } else {
            jsonError("Playlist color failed")
        }
    }

    private fun handleReorderPlaylists(session: IHTTPSession): Response {
        val handler = onReorderPlaylists ?: return jsonError("Playlist reorder not available")
        val playlistIds = parseLongArray(readPostBody(session), "playlistIds")
            ?: return jsonError("Missing playlistIds")
        if (playlistIds.isEmpty()) return jsonError("Empty playlist list")
        return if (handler(playlistIds).isSuccess) {
            jsonResponse(buildPlaylistsListJson())
        } else {
            jsonError("Playlist reorder failed")
        }
    }

    private fun handleCreatePlaylist(session: IHTTPSession): Response {
        val handler = onCreatePlaylist ?: return jsonError("Playlist create not available")
        val raw = readPostBody(session)
        val name = parseStringField(raw, "name") ?: return jsonError("Missing name")
        if (name.isBlank()) return jsonError("Missing name")
        val result = handler(name)
        return if (result.isSuccess) {
            val id = result.getOrThrow()
            jsonResponse("""{"id":$id,"name":${jsonStr(name)}}""")
        } else {
            jsonError("Playlist create failed")
        }
    }

    private fun handleMatchQuickstart(session: IHTTPSession): Response {
        val handler = onMatchQuickstart ?: return jsonError("Quickstart match not available")
        val raw = readPostBody(session)
        val text = parseStringField(raw, "text") ?: return jsonError("Missing text")
        val results = handler(text)
        val sb = StringBuilder("""{"results":[""")
        results.forEachIndexed { i, result ->
            if (i > 0) sb.append(',')
            val songIdJson = result.songId?.toString() ?: "null"
            val titleJson = result.title?.let { jsonStr(it) } ?: "null"
            val keyJson = result.key?.let { jsonStr(it) } ?: "null"
            sb.append(
                """{"line":${jsonStr(result.line)},"songId":$songIdJson,"title":$titleJson,"key":$keyJson}""",
            )
        }
        sb.append("]}")
        return jsonResponse(sb.toString())
    }

    private fun handleCreateQuickstart(session: IHTTPSession): Response {
        val handler = onCreateQuickstart ?: return jsonError("Quickstart create not available")
        val raw = readPostBody(session)
        val name = parseStringField(raw, "name") ?: return jsonError("Missing name")
        if (name.isBlank()) return jsonError("Missing name")
        val text = parseStringField(raw, "text") ?: return jsonError("Missing text")
        val withPlaceholders = Regex(""""withPlaceholders"\s*:\s*true""").containsMatchIn(raw)
        val result = handler(name, text, withPlaceholders)
        return if (result.isSuccess) {
            val id = result.getOrThrow()
            jsonResponse("""{"id":$id,"name":${jsonStr(name)}}""")
        } else {
            jsonError(result.exceptionOrNull()?.message ?: "Quickstart create failed")
        }
    }

    private fun handleDeletePlaylist(playlistId: Long): Response {
        val handler = onDeletePlaylist ?: return jsonError("Playlist delete not available")
        return if (handler(playlistId).isSuccess) {
            jsonResponse(buildPlaylistsListJson())
        } else {
            jsonError("Playlist delete failed")
        }
    }

    private fun parseUploadRequest(session: IHTTPSession): Pair<ParsedUpload?, Response?> {
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
        } catch (_: Exception) {
            return null to jsonError("Invalid upload")
        }
        val title = session.parameters["title"]?.firstOrNull().orEmpty()
        val key = session.parameters["key"]?.firstOrNull().orEmpty()
        val notes = session.parameters["notes"]?.firstOrNull().orEmpty()
        val rawFilename = session.parameters["filename"]?.firstOrNull().orEmpty()
        val tempPath = files["file"] ?: return null to jsonError("Missing file")
        val tempFile = File(tempPath)
        if (!tempFile.exists()) return null to jsonError("Missing file")
        val mimeType = session.parameters["mime"]?.firstOrNull()
            ?: guessMimeType(tempFile)
        if (!isAllowedMime(mimeType)) return null to jsonError("Unsupported file type")
        val parsed = SongTitleMigration.parse(rawFilename.ifBlank { tempFile.name })
        val resolvedTitle = title.trim().ifBlank { parsed.title }
        val resolvedKey = key.trim().ifBlank { parsed.keySignature }
        val resolvedNotes = notes.trim().ifBlank { parsed.notes }
        return ParsedUpload(resolvedTitle, resolvedKey, resolvedNotes, tempFile, mimeType) to null
    }

    private fun handleCatalogUpload(session: IHTTPSession): Response {
        val handler = onUploadSong ?: return jsonError("Upload not available")
        val (upload, error) = parseUploadRequest(session)
        if (error != null) return error
        val parsed = upload ?: return jsonError("Invalid upload")
        val result = handler(parsed.title, parsed.key, parsed.notes, parsed.tempFile, parsed.mimeType)
        return if (result.isSuccess) {
            jsonResponse(buildSongsResponseJson())
        } else {
            jsonError(result.exceptionOrNull()?.message ?: "Upload failed")
        }
    }

    private fun handleUpload(playlistId: Long, session: IHTTPSession): Response {
        val handler = onUpload ?: return jsonError("Upload not available")
        val (upload, error) = parseUploadRequest(session)
        if (error != null) return error
        val parsed = upload ?: return jsonError("Invalid upload")
        val result = handler(
            playlistId,
            parsed.title,
            parsed.key,
            parsed.notes,
            parsed.tempFile,
            parsed.mimeType,
        )
        return if (result.isSuccess) {
            handleState(playlistId)
        } else {
            jsonError(result.exceptionOrNull()?.message ?: "Upload failed")
        }
    }

    private fun handleParseFilename(session: IHTTPSession): Response {
        val raw = session.parameters["raw"]?.firstOrNull().orEmpty()
        val parsed = SongTitleMigration.parse(raw)
        return jsonResponse(
            """{"title":${jsonStr(parsed.title)},"key":${jsonStr(parsed.keySignature)},"notes":${jsonStr(parsed.notes)}}""",
        )
    }

    private fun isAllowedMime(mimeType: String): Boolean =
        mimeType.startsWith("image/") || mimeType == "application/pdf"

    private fun guessMimeType(file: File): String {
        file.inputStream().use { input ->
            val header = ByteArray(4)
            if (input.read(header) == 4 &&
                header[0] == '%'.code.toByte() &&
                header[1] == 'P'.code.toByte() &&
                header[2] == 'D'.code.toByte() &&
                header[3] == 'F'.code.toByte()
            ) {
                return "application/pdf"
            }
        }
        return when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }

    private fun jsonError(message: String): Response =
        newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            "application/json",
            """{"error":${jsonStr(message)}}""",
        )

    private fun handleAuth(session: IHTTPSession): Response {
        val raw = readPostBody(session)
        val submittedPin = Regex(""""pin"\s*:\s*"(\d{5})"""").find(raw)?.groupValues?.get(1)
        if (submittedPin != pin) {
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                """{"error":${jsonStr("Invalid PIN")}}""",
            )
        }
        val response = jsonResponse("""{"ok":true}""")
        response.addHeader("Set-Cookie", "remote_auth=$sessionToken; Path=/; HttpOnly; SameSite=Lax")
        return response
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val cookieHeader = session.headers["cookie"] ?: return false
        return cookieHeader.split(';').any { part ->
            val trimmed = part.trim()
            trimmed.startsWith("remote_auth=") &&
                trimmed.removePrefix("remote_auth=").trim() == sessionToken
        }
    }

    private fun jsonUnauthorized(): Response =
        newFixedLengthResponse(
            Response.Status.UNAUTHORIZED,
            "application/json",
            """{"error":${jsonStr("Authentication required")}}""",
        )

    private fun clampPlayback(songs: List<RemoteSong>, state: PlaybackState) {
        synchronized(state) {
            if (songs.isEmpty()) {
                state.songIndex = 0
                state.pageIndex = 0
                return
            }
            state.songIndex = state.songIndex.coerceIn(0, songs.lastIndex)
            val pages = songs[state.songIndex].pageCount.coerceAtLeast(1)
            state.pageIndex = state.pageIndex.coerceIn(0, pages - 1)
        }
    }

    private fun resetPlayback(state: PlaybackState) {
        synchronized(state) {
            state.songIndex = 0
            state.pageIndex = 0
        }
    }

    private fun step(songs: List<RemoteSong>, state: PlaybackState, delta: Int) {
        if (songs.isEmpty()) return
        synchronized(state) {
            val song = songs[state.songIndex]
            val pages = song.pageCount.coerceAtLeast(1)
            if (delta > 0) {
                if (state.pageIndex < pages - 1) {
                    state.pageIndex++
                } else if (state.songIndex < songs.lastIndex) {
                    state.songIndex++
                    state.pageIndex = 0
                }
            } else {
                if (state.pageIndex > 0) {
                    state.pageIndex--
                } else if (state.songIndex > 0) {
                    state.songIndex--
                    state.pageIndex = songs[state.songIndex].pageCount.coerceAtLeast(1) - 1
                }
            }
        }
    }

    private fun serveMedia(playlistId: Long, session: IHTTPSession): Response {
        val loaded = loadPlaylist(playlistId) ?: return jsonNotFound("Playlist not found")
        val songs = loaded.songs
        if (songs.isEmpty()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No songs")
        }
        val state = playback(playlistId)
        clampPlayback(songs, state)
        val params = session.parameters
        val sIdx = params["song"]?.firstOrNull()?.toIntOrNull()?.coerceIn(0, songs.lastIndex)
            ?: state.songIndex
        val pIdx = params["page"]?.firstOrNull()?.toIntOrNull() ?: state.pageIndex
        val song = songs[sIdx]
        val file = SongStoragePaths.resolve(song.filePath)
        if (!file.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File missing")
        }
        return when (song.fileType) {
            "PDF" -> {
                val bitmap = PdfHelper.renderPage(file, pIdx, 1400) ?: return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Page missing",
                )
                val bytes = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, bytes)
                bitmap.recycle()
                val data = bytes.toByteArray()
                newFixedLengthResponse(
                    Response.Status.OK,
                    "image/jpeg",
                    ByteArrayInputStream(data),
                    data.size.toLong(),
                )
            }
            else -> {
                val mime = when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                newFixedLengthResponse(
                    Response.Status.OK,
                    mime,
                    FileInputStream(file),
                    file.length(),
                )
            }
        }
    }

    private fun serveDownload(playlistId: Long, session: IHTTPSession): Response {
        val loaded = loadPlaylist(playlistId) ?: return jsonNotFound("Playlist not found")
        val songs = loaded.songs
        if (songs.isEmpty()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No songs")
        }
        val state = playback(playlistId)
        clampPlayback(songs, state)
        val params = session.parameters
        val sIdx = params["song"]?.firstOrNull()?.toIntOrNull()?.coerceIn(0, songs.lastIndex)
            ?: state.songIndex
        val song = songs[sIdx]
        val file = SongStoragePaths.resolve(song.filePath)
        if (!file.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File missing")
        }
        val ext = SongFileNames.extensionOf(file.name)
        val downloadName = SongFileNames.mediaFileName(song.title, song.songId, ext)
        val mime = when (song.fileType) {
            "PDF" -> "application/pdf"
            else -> when (ext) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
        }
        val response = newFixedLengthResponse(
            Response.Status.OK,
            mime,
            FileInputStream(file),
            file.length(),
        )
        response.addHeader("Content-Disposition", "attachment; filename=\"$downloadName\"")
        return response
    }

    private fun jsonNotFound(message: String): Response =
        newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            "application/json",
            """{"error":${jsonStr(message)}}""",
        )

    private fun buildStateJson(loaded: PlaylistLoad, state: PlaybackState): String {
        val sb = StringBuilder()
        sb.append(
            """{"playlistId":${loaded.playlistId},"playlistName":${jsonStr(loaded.playlistName)},"songIndex":${state.songIndex},"pageIndex":${state.pageIndex},"songs":[""",
        )
        loaded.songs.forEachIndexed { i, song ->
            if (i > 0) sb.append(',')
            sb.append(
                """{"title":${jsonStr(song.title)},"key":${jsonStr(song.keySignature)},"fileType":${jsonStr(song.fileType)},"pageCount":${song.pageCount}}""",
            )
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun buildEntriesJson(loaded: PlaylistLoad, state: PlaybackState): String {
        val sb = StringBuilder()
        sb.append(
            """{"playlistId":${loaded.playlistId},"playlistName":${jsonStr(loaded.playlistName)},"songIndex":${state.songIndex},"entries":[""",
        )
        loaded.songs.forEachIndexed { i, song ->
            if (i > 0) sb.append(',')
            sb.append(
                """{"entryId":${song.entryId},"songId":${song.songId},"title":${jsonStr(song.title)},"key":${jsonStr(song.keySignature)},"notes":${jsonStr(song.notes)},"fileType":${jsonStr(song.fileType)},"pageCount":${song.pageCount}}""",
            )
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun readPostBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
        } catch (_: Exception) {
        }
        return files["postData"].orEmpty()
    }

    private fun parseLongField(raw: String, field: String): Long? =
        Regex(""""$field"\s*:\s*(\d+)""").find(raw)?.groupValues?.get(1)?.toLongOrNull()

    private fun parseStringField(raw: String, field: String): String? {
        val match = Regex(""""$field"\s*:\s*"((?:\\.|[^"\\])*)"""").find(raw) ?: return null
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
    }

    private fun parseLongArray(raw: String, field: String): List<Long>? {
        val match = Regex(""""$field"\s*:\s*\[([^\]]*)]""").find(raw) ?: return null
        val inner = match.groupValues[1].trim()
        if (inner.isEmpty()) return emptyList()
        return inner.split(',').mapNotNull { it.trim().toLongOrNull() }
    }

    private fun jsonStr(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun htmlResponse(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun jsResponse(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/javascript; charset=utf-8", body)

    private fun cssResponse(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/css; charset=utf-8", body)

    private fun jsonResponse(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", body)
}
