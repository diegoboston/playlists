package com.playlists.app.remote

import android.graphics.Bitmap
import com.playlists.app.ui.PdfHelper
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class PlayRemoteServer(
    hostname: String,
    port: Int,
    private val pin: String,
    private val requirePin: Boolean = true,
    private val playlistName: String,
    songs: List<RemoteSong>,
    private val html: String,
    private val editHtml: String,
    private val pinHtml: String,
    private val onStopRequested: () -> Unit = {},
    private val onUpload: ((title: String, key: String, notes: String, tempFile: File, mimeType: String) -> Result<Unit>)? = null,
    private val onReorder: ((entryIds: List<Long>) -> Result<Unit>)? = null,
    private val onRemove: ((entryId: Long) -> Result<Unit>)? = null,
    private val onAdd: ((songId: Long) -> Result<Unit>)? = null,
    private val onSearchSongs: ((query: String) -> List<SearchSong>)? = null,
) : NanoHTTPD(hostname, port) {

    private val songs: MutableList<RemoteSong> = songs.toMutableList()
    private val sessionToken: String = UUID.randomUUID().toString()

    data class RemoteSong(
        val entryId: Long,
        val songId: Long,
        val title: String,
        val keySignature: String,
        val notes: String,
        val fileType: String,
        val filePath: String,
        val pageCount: Int,
        val isDeleted: Boolean,
    )

    data class SearchSong(
        val id: Long,
        val title: String,
        val keySignature: String,
        val notes: String,
    )

    @Volatile
    var songIndex: Int = 0
        private set

    @Volatile
    var pageIndex: Int = 0
        private set

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.substringBefore('?')
        if (uri == "/api/auth" && session.method == Method.POST) {
            return handleAuth(session)
        }
        if (requirePin && !isAuthorized(session)) {
            return when {
                uri == "/" || uri == "/index.html" || uri == "/edit" || uri == "/edit.html" ->
                    htmlResponse(pinHtml)
                uri.startsWith("/api/") -> jsonUnauthorized()
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        }
        return when {
            uri == "/" || uri == "/index.html" -> htmlResponse(html)
            uri == "/edit" || uri == "/edit.html" -> htmlResponse(editHtml)
            uri == "/api/state" -> jsonResponse(buildStateJson())
            uri == "/api/playlist" -> jsonResponse(buildPlaylistJson())
            uri == "/api/songs/search" -> handleSearch(session)
            uri == "/api/navigate" && session.method == Method.POST -> handleNavigate(session)
            uri == "/api/stop" && session.method == Method.POST -> {
                onStopRequested()
                jsonResponse("""{"stopped":true}""")
            }
            uri == "/api/upload" && session.method == Method.POST -> handleUpload(session)
            uri == "/api/reorder" && session.method == Method.POST -> handleReorder(session)
            uri == "/api/remove" && session.method == Method.POST -> handleRemove(session)
            uri == "/api/add" && session.method == Method.POST -> handleAdd(session)
            uri == "/api/media" -> serveMedia(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun handleNavigate(session: IHTTPSession): Response {
        val raw = readPostBody(session)
        val direction = Regex(""""direction"\s*:\s*"(\w+)"""").find(raw)?.groupValues?.get(1)
        when (direction) {
            "next" -> step(1)
            "prev" -> step(-1)
        }
        return jsonResponse(buildStateJson())
    }

    private fun handleReorder(session: IHTTPSession): Response {
        val handler = onReorder ?: return jsonError("Reorder not available")
        val entryIds = parseLongArray(readPostBody(session), "entryIds")
            ?: return jsonError("Missing entryIds")
        if (entryIds.isEmpty()) return jsonError("Empty playlist")
        return if (handler(entryIds).isSuccess) {
            jsonResponse(buildPlaylistJson())
        } else {
            jsonError("Reorder failed")
        }
    }

    private fun handleRemove(session: IHTTPSession): Response {
        val handler = onRemove ?: return jsonError("Remove not available")
        val entryId = parseLongField(readPostBody(session), "entryId")
            ?: return jsonError("Missing entryId")
        return if (handler(entryId).isSuccess) {
            jsonResponse(buildPlaylistJson())
        } else {
            jsonError("Remove failed")
        }
    }

    private fun handleAdd(session: IHTTPSession): Response {
        val handler = onAdd ?: return jsonError("Add not available")
        val songId = parseLongField(readPostBody(session), "songId")
            ?: return jsonError("Missing songId")
        return if (handler(songId).isSuccess) {
            jsonResponse(buildPlaylistJson())
        } else {
            jsonError("Add failed")
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

    fun replaceSongs(newSongs: List<RemoteSong>) {
        synchronized(songs) {
            val currentEntryId = songs.getOrNull(songIndex)?.entryId
            songs.clear()
            songs.addAll(newSongs)
            if (songs.isEmpty()) {
                songIndex = 0
                pageIndex = 0
            } else {
                val newIndex = if (currentEntryId != null) {
                    val idx = songs.indexOfFirst { it.entryId == currentEntryId }
                    if (idx >= 0) idx else minOf(songIndex, songs.lastIndex)
                } else {
                    songIndex.coerceIn(0, songs.lastIndex)
                }
                songIndex = newIndex
                val pages = songs[songIndex].pageCount.coerceAtLeast(1)
                pageIndex = pageIndex.coerceIn(0, pages - 1)
            }
        }
    }

    fun goToSong(index: Int) {
        synchronized(songs) {
            if (songs.isEmpty()) return
            songIndex = index.coerceIn(0, songs.lastIndex)
            pageIndex = 0
        }
    }

    private fun handleUpload(session: IHTTPSession): Response {
        val handler = onUpload
            ?: return jsonError("Upload not available")
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
        } catch (_: Exception) {
            return jsonError("Invalid upload")
        }
        val title = session.parameters["title"]?.firstOrNull().orEmpty()
        val key = session.parameters["key"]?.firstOrNull().orEmpty()
        val notes = session.parameters["notes"]?.firstOrNull().orEmpty()
        val tempPath = files["file"] ?: return jsonError("Missing file")
        val tempFile = File(tempPath)
        if (!tempFile.exists()) return jsonError("Missing file")
        val mimeType = session.parameters["mime"]?.firstOrNull()
            ?: guessMimeType(tempFile)
        if (!isAllowedMime(mimeType)) return jsonError("Unsupported file type")
        val result = handler(title, key, notes, tempFile, mimeType)
        return if (result.isSuccess) {
            jsonResponse(buildStateJson())
        } else {
            jsonError(result.exceptionOrNull()?.message ?: "Upload failed")
        }
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

    private fun step(delta: Int) {
        if (songs.isEmpty()) return
        val song = songs[songIndex]
        val pages = song.pageCount.coerceAtLeast(1)
        if (delta > 0) {
            if (pageIndex < pages - 1) {
                pageIndex++
            } else if (songIndex < songs.lastIndex) {
                songIndex++
                pageIndex = 0
            }
        } else {
            if (pageIndex > 0) {
                pageIndex--
            } else if (songIndex > 0) {
                songIndex--
                pageIndex = songs[songIndex].pageCount.coerceAtLeast(1) - 1
            }
        }
    }

    private fun serveMedia(session: IHTTPSession): Response {
        if (songs.isEmpty()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No songs")
        }
        val params = session.parameters
        val sIdx = params["song"]?.firstOrNull()?.toIntOrNull()?.coerceIn(0, songs.lastIndex) ?: songIndex
        val pIdx = params["page"]?.firstOrNull()?.toIntOrNull() ?: 0
        val song = songs[sIdx]
        val file = File(song.filePath)
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

    private fun buildStateJson(): String {
        val sb = StringBuilder()
        sb.append("""{"playlistName":${jsonStr(playlistName)},"songIndex":$songIndex,"pageIndex":$pageIndex,"songs":[""")
        songs.forEachIndexed { i, song ->
            if (i > 0) sb.append(',')
            sb.append("""{"title":${jsonStr(song.title)},"fileType":${jsonStr(song.fileType)},"pageCount":${song.pageCount}}""")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun buildPlaylistJson(): String {
        val sb = StringBuilder()
        sb.append(
            """{"playlistName":${jsonStr(playlistName)},"songIndex":$songIndex,"entries":[""",
        )
        songs.forEachIndexed { i, song ->
            if (i > 0) sb.append(',')
            sb.append(
                """{"entryId":${song.entryId},"songId":${song.songId},"title":${jsonStr(song.title)},"key":${jsonStr(song.keySignature)},"notes":${jsonStr(song.notes)},"fileType":${jsonStr(song.fileType)},"pageCount":${song.pageCount},"isDeleted":${song.isDeleted}}""",
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

    private fun jsonResponse(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", body)
}
