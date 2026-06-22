package com.playlists.app.remote

import android.graphics.Bitmap
import com.playlists.app.ui.PdfHelper
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

class PlayRemoteServer(
    port: Int,
    private val playlistName: String,
    songs: List<RemoteSong>,
    private val html: String,
    private val onStopRequested: () -> Unit = {},
    private val onUpload: ((title: String, key: String, notes: String, tempFile: File, mimeType: String) -> Result<Unit>)? = null,
) : NanoHTTPD(port) {

    private val songs: MutableList<RemoteSong> = songs.toMutableList()

    data class RemoteSong(
        val title: String,
        val fileType: String,
        val filePath: String,
        val pageCount: Int,
    )

    @Volatile
    var songIndex: Int = 0
        private set

    @Volatile
    var pageIndex: Int = 0
        private set

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.substringBefore('?')
        return when {
            uri == "/" || uri == "/index.html" -> htmlResponse(html)
            uri == "/api/state" -> jsonResponse(buildStateJson())
            uri == "/api/navigate" && session.method == Method.POST -> handleNavigate(session)
            uri == "/api/stop" && session.method == Method.POST -> {
                onStopRequested()
                jsonResponse("""{"stopped":true}""")
            }
            uri == "/api/upload" && session.method == Method.POST -> handleUpload(session)
            uri == "/api/media" -> serveMedia(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun handleNavigate(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
        } catch (_: Exception) {
        }
        val raw = files["postData"].orEmpty()
        val direction = Regex(""""direction"\s*:\s*"(\w+)"""").find(raw)?.groupValues?.get(1)
        when (direction) {
            "next" -> step(1)
            "prev" -> step(-1)
        }
        return jsonResponse(buildStateJson())
    }

    fun replaceSongs(newSongs: List<RemoteSong>) {
        synchronized(songs) {
            songs.clear()
            songs.addAll(newSongs)
            if (songs.isEmpty()) {
                songIndex = 0
                pageIndex = 0
            } else {
                songIndex = songIndex.coerceIn(0, songs.lastIndex)
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
        return when (val result = handler(title, key, notes, tempFile, mimeType)) {
            is Result.Success -> jsonResponse(buildStateJson())
            is Result.Failure -> jsonError(result.exceptionOrNull()?.message ?: "Upload failed")
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

    private fun jsonStr(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun htmlResponse(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun jsonResponse(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", body)
}
