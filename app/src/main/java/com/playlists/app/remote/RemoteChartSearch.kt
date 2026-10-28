package com.playlists.app.remote

import android.content.Context
import com.playlists.app.PlaylistsApp
import com.playlists.app.ai.ChartAssistantService
import com.playlists.app.ai.ChartDraft
import com.playlists.app.ai.ChartIntent
import com.playlists.app.ai.ChartSearchMode
import com.playlists.app.ai.OpenAiClient
import com.playlists.app.data.FileType
import com.playlists.app.data.Song
import com.playlists.app.find.SearchResult
import com.playlists.app.find.WebSearchService
import com.playlists.app.render.ChartPdfRenderer
import com.playlists.app.util.AiCredentialStore
import com.playlists.app.util.ChartDraftStore
import com.playlists.app.util.FileStorage
import com.playlists.app.util.SongStoragePaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** Short-lived, playlist-scoped previews. Slow searches run outside the HTTP request. */
class RemoteChartSearch(
    private val search: suspend (ChartIntent, Int) -> List<SearchResult>,
    private val extract: suspend (List<SearchResult>, ChartIntent) -> ChartDraft,
    private val render: (ChartDraft) -> ByteArray,
    private val save: suspend (Long, ChartDraft, ByteArray, ChartSearchMode) -> Unit,
    private val available: () -> Boolean,
    private val playlistExists: suspend (Long) -> Boolean,
    private val extractText: suspend (String, ChartSearchMode) -> ChartDraft = { _, _ ->
        error("Text import unavailable")
    },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = mutableMapOf<String, Session>()
    private class Session(val playlistId: Long, val intent: ChartIntent) {
        var state = "processing"
        var error: String? = null
        var nextOffset = BATCH_SIZE
        var hasNextBatch = true
        var draft: ChartDraft? = null
        var pdf: ByteArray? = null
        var job: Job? = null
        var touched = System.currentTimeMillis()
    }

    @Synchronized
    fun request(playlistId: Long, action: String, body: JSONObject): JSONObject {
        sessions.entries.removeAll {
            val expired = System.currentTimeMillis() - it.value.touched > 30 * 60_000 && it.value.state != "saving"
            if (expired) it.value.job?.cancel()
            expired
        }
        if (action == "available") return JSONObject().put("available", available())
        if (action == "start") {
            check(available()) { "Add OpenAI API key in the app Settings" }
            check(sessions.values.count { it.state != "saved" } < 8) { "Too many searches. Cancel a search or try again later." }
            val mode = when (body.optString("mode", "chords")) {
                "chords" -> ChartSearchMode.ChordsAndLyrics
                "lyrics" -> ChartSearchMode.LyricsOnly
                else -> error("Invalid search mode")
            }
            val input = body.optString("input", "search")
            require(input == "search" || input == "text") { "Invalid input mode" }
            val pastedText = body.optString("text").trim()
            if (input == "text") {
                require(pastedText.isNotBlank()) { "Paste some song text" }
                require(pastedText.length <= 30_000) { "Song text is too long (maximum 30,000 characters)" }
            }
            val query = if (input == "text") "Pasted song" else body.optString("query").trim()
            require(query.length <= 500) { "Search query is too long" }
            val intent = requireNotNull(ChartIntent.fromTypedQuery(query, mode)) { "Enter a song title" }
            val token = UUID.randomUUID().toString()
            val session = Session(playlistId, intent)
            if (input == "text") session.hasNextBatch = false
            sessions[token] = session
            work(session) {
                check(playlistExists(playlistId)) { "Playlist not found" }
                if (input == "text") {
                    val draft = extractText(pastedText, mode)
                    currentCoroutineContext().ensureActive()
                    val pdf = render(draft)
                    synchronized(this) {
                        session.hasNextBatch = false
                        session.draft = draft
                        session.pdf = pdf
                        session.state = "preview"
                    }
                } else {
                    loadBatch(session, 0)
                }
            }
            return snapshot(token, session)
        }
        val token = body.optString("token")
        val session = sessions[token]?.takeIf { it.playlistId == playlistId }
            ?: error("Search expired. Start a new search.")
        session.touched = System.currentTimeMillis()
        when (action) {
            "status" -> Unit
            "cancel" -> {
                check(session.state != "saving") { "Saving chart. Please wait." }
                session.job?.cancel()
                sessions.remove(token)
                return JSONObject().put("state", "cancelled")
            }
            "next" -> {
                check(session.state == "preview") { "No preview available" }
                check(session.hasNextBatch) { "No more search results" }
                session.state = "processing"
                session.draft = null
                session.pdf = null
                val offset = session.nextOffset
                session.nextOffset += BATCH_SIZE
                work(session) { loadBatch(session, offset) }
            }
            "save" -> {
                // Keep the saved state until expiry so retries cannot insert the song twice.
                if (session.state == "saved" || session.state == "saving") return snapshot(token, session)
                check(session.state == "preview") { "No preview available" }
                val draft = requireNotNull(session.draft)
                val pdf = requireNotNull(session.pdf)
                session.state = "saving"
                work(session) {
                    check(playlistExists(playlistId)) { "Playlist not found" }
                    save(playlistId, draft, pdf, session.intent.searchMode)
                    synchronized(this) {
                        session.state = "saved"
                        session.pdf = null
                    }
                }
            }
            else -> error("Unknown search action")
        }
        return snapshot(token, session)
    }

    @Synchronized
    fun preview(playlistId: Long, token: String): ByteArray? = sessions[token]
        ?.takeIf { it.playlistId == playlistId && it.state == "preview" }?.pdf

    @Synchronized
    fun close() {
        scope.cancel()
        sessions.clear()
    }

    private fun work(session: Session, block: suspend () -> Unit) {
        session.job = scope.launch {
            try {
                block()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                synchronized(this@RemoteChartSearch) {
                    session.error = error.message ?: "AI song search failed"
                    session.state = "error"
                }
            }
        }
    }

    private suspend fun loadBatch(session: Session, offset: Int) {
        val results = search(session.intent, offset)
        session.hasNextBatch = results.size == BATCH_SIZE
        if (results.isEmpty()) throw IllegalStateException("No search results")
        currentCoroutineContext().ensureActive()
        val draft = extract(results, session.intent)
        currentCoroutineContext().ensureActive()
        val pdf = render(draft)
        synchronized(this) {
            session.draft = draft
            session.pdf = pdf
            session.state = "preview"
        }
    }

    private fun snapshot(token: String, session: Session): JSONObject = JSONObject()
        .put("token", token).put("state", session.state)
        .put("hasNext", session.hasNextBatch)
        .put("title", session.draft?.title.orEmpty())
        .put("error", session.error ?: JSONObject.NULL)

    companion object {
        private const val BATCH_SIZE = 5

        fun create(context: Context): RemoteChartSearch {
            val app = context.applicationContext as PlaylistsApp
            return RemoteChartSearch(
                search = { intent, offset -> WebSearchService.search(intent.searchQuery(), offset = offset) },
                extract = { results, intent ->
                    val key = checkNotNull(AiCredentialStore.getOpenAiApiKey(context)) {
                        "Add OpenAI API key in the app Settings"
                    }
                    val draft = ChartAssistantService(OpenAiClient(key)).fetchAndExtractChartBatch(results, intent)
                    draft.copy(bodyTextSize = ChartPdfRenderer.resolvedBodyTextSize(draft))
                },
                extractText = { text, mode ->
                    val key = checkNotNull(AiCredentialStore.getOpenAiApiKey(context)) {
                        "Add OpenAI API key in the app Settings"
                    }
                    val draft = OpenAiClient(key).extractPastedText(text, mode)
                        ?: error("Could not extract a song from the pasted text")
                    draft.copy(bodyTextSize = ChartPdfRenderer.resolvedBodyTextSize(draft))
                },
                render = { ChartPdfRenderer.render(it, it.bodyTextSize) },
                save = { playlistId, draft, pdf, mode ->
                    val file = FileStorage.storeBytes(pdf, "pdf")
                    val path = SongStoragePaths.toStoredPath(file)
                    var songId: Long? = null
                    try {
                        ChartDraftStore.save(draft, path)
                        songId = app.songRepository.insert(Song(
                            title = draft.title,
                            keySignature = draft.key.orEmpty(),
                            notes = listOfNotNull(
                                if (mode == ChartSearchMode.LyricsOnly) Song.NOTES_AI_LYRICS else Song.NOTES_AI_CHART,
                                draft.sourceUrl, draft.artist,
                            ).joinToString(" · "),
                            filePath = path, fileType = FileType.PDF.name,
                        ))
                        app.playlistRepository.addSong(playlistId, songId)
                    } catch (error: Exception) {
                        songId?.let { app.songRepository.delete(it) }
                        ChartDraftStore.deleteIfPresent(path)
                        file.delete()
                        throw error
                    }
                },
                available = { AiCredentialStore.getOpenAiApiKey(context) != null },
                playlistExists = { app.playlistRepository.getById(it) != null },
            )
        }
    }

}
