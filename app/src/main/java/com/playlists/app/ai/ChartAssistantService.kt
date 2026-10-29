package com.playlists.app.ai

import com.playlists.app.find.PageFetcher
import com.playlists.app.find.SearchResult
import com.playlists.app.find.WebSearchService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class OpenAiClient(
    private val apiKey: String,
    private val httpClient: OkHttpClient = defaultClient(),
    private val diagnostic: (String) -> Unit = {},
) {
    fun transcribeAudio(audioFile: File): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/mp4".toMediaType()),
            )
            .build()
        val request = Request.Builder()
            .url("$API_BASE/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        val json = postJson(request)
        return json.optString("text").trim()
    }

    fun parseIntent(
        transcript: String,
        playlistsContextJson: String,
        searchMode: ChartSearchMode = ChartSearchMode.ChordsAndLyrics,
    ): ChartIntent? {
        val content = chatJson(AiPrompts.parseIntentSystem(playlistsContextJson), transcript)
            ?: return null
        return AiJsonHelper.parseObject(content)?.let {
            ChartIntent.fromJson(it, transcript, searchMode)
        }
    }

    fun extractChart(
        pageText: String,
        songTitle: String,
        artist: String?,
        sourceUrl: String,
        searchMode: ChartSearchMode = ChartSearchMode.ChordsAndLyrics,
    ): ChartDraft? {
        val lyricsOnly = searchMode == ChartSearchMode.LyricsOnly
        val content = chatJson(
            AiPrompts.extractChartSystem(
                lyricsOnly = lyricsOnly,
                sourceUrl = sourceUrl,
                songTitle = songTitle,
                artist = artist,
            ),
            pageText.take(30_000),
            jsonObject = false,
        ) ?: return null
        return ChartTextParser.parse(content, sourceUrl = sourceUrl, fallbackTitle = songTitle)
            ?.let { draft -> if (lyricsOnly) draft.withoutChords() else draft }
    }

    fun extractPastedText(text: String, searchMode: ChartSearchMode): ChartDraft? {
        require(text.isNotBlank()) { "Paste some song text" }
        require(text.length <= 30_000) { "Song text is too long (maximum 30,000 characters)" }
        val lines = PastedSongParser.lines(text)
        diagnostic("Pasted input: ${text.length} characters, ${lines.size} nonempty lines. Classifying lines; original text will be preserved.")
        val plan = JSONObject()
        val allKinds = JSONObject()
        // Bound the schema size and response length for long copied pages.
        lines.chunked(100).forEachIndexed { batchIndex, batch ->
            diagnostic("Classifying batch ${batchIndex + 1}: ${batch.size} lines using required line IDs.")
            val content = chatJson(
                AiPrompts.CLASSIFY_PASTED_TEXT, PastedSongParser.indexedInput(batch).toString(),
                temperature = 0.0, requireComplete = true,
                responseFormat = PastedSongParser.responseFormat(batch.size),
            ) ?: throw OpenAiException("Empty classification response. Nothing was imported.")
            val batchPlan = AiJsonHelper.parseObject(content)
                ?: throw OpenAiException("Invalid classification JSON. Nothing was imported.")
            val kinds = batchPlan.getJSONObject("lineKinds")
            require(kinds.length() == batch.size && batch.indices.all { kinds.has((it + 1).toString()) }) {
                "Incomplete line classification: received ${kinds.length()} of ${batch.size} lines in batch ${batchIndex + 1}. Nothing was imported."
            }
            batch.indices.forEach { index ->
                allKinds.put((batchIndex * 100 + index + 1).toString(), kinds.getString((index + 1).toString()))
            }
            listOf("title", "artist", "key", "capo").forEach { field ->
                if (plan.optString(field).normalizeOptionalField() == null) {
                    plan.put(field, batchPlan.optString(field))
                }
            }
        }
        plan.put("lineKinds", allKinds)
        return PastedSongParser.parse(text, plan, searchMode, diagnostic)
    }

    fun extractChartFromCandidates(
        candidates: List<ChartCandidatePage>,
        intent: ChartIntent,
    ): ChartDraft? {
        val sourceText = candidates.mapIndexed { index, candidate ->
            "--- CANDIDATE ${index + 1} ---\nTITLE: ${candidate.result.title}\nURL: ${candidate.result.url}\n${candidate.text}"
        }.joinToString("\n\n").take(40_000)
        val system = AiPrompts.extractChartSystem(
            lyricsOnly = intent.searchMode == ChartSearchMode.LyricsOnly,
            sourceUrl = "multiple candidate pages",
            songTitle = intent.songTitle,
            artist = intent.artist,
        ) + "\n" + AiPrompts.EXTRACT_CHART_MULTI_SOURCE_RULES
        val content = chatJson(system, sourceText, jsonObject = false) ?: return null
        return ChartTextParser.parse(content, sourceUrl = candidates.first().result.url, fallbackTitle = intent.songTitle)
            ?.let { draft -> if (intent.searchMode == ChartSearchMode.LyricsOnly) draft.withoutChords() else draft }
    }

    /**
     * Read a song title from a chart/set-list photo. Returns empty string when none is readable.
     */
    fun extractTitleFromImage(imageBytes: ByteArray, mimeType: String = "image/jpeg"): String {
        val b64 = java.util.Base64.getEncoder().encodeToString(imageBytes)
        val dataUrl = "data:$mimeType;base64,$b64"
        val userContent = JSONArray()
            .put(
                JSONObject().put("type", "text").put("text", AiPrompts.EXTRACT_TITLE_FROM_IMAGE_USER),
            )
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject()
                            .put("url", dataUrl)
                            .put("detail", "low"),
                    ),
            )
        val content = chatJson(
            AiPrompts.EXTRACT_TITLE_FROM_IMAGE_SYSTEM,
            userContent,
            maxTokens = 200,
            temperature = 0.0,
        ) ?: return ""
        return AiJsonHelper.parseObject(content)?.optString("title")?.trim().orEmpty()
    }

    private fun chatJson(
        system: String,
        userContent: Any,
        maxTokens: Int? = null,
        temperature: Double? = null,
        jsonObject: Boolean = true,
        requireComplete: Boolean = false,
        responseFormat: JSONObject? = null,
    ): String? {
        val payload = JSONObject()
            .put("model", CHAT_MODEL)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", userContent)),
            )
        if (responseFormat != null) {
            payload.put("response_format", responseFormat)
        } else if (jsonObject) {
            payload.put("response_format", JSONObject().put("type", "json_object"))
        }
        if (maxTokens != null) payload.put("max_tokens", maxTokens)
        if (temperature != null) payload.put("temperature", temperature)
        val request = Request.Builder()
            .url("$API_BASE/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val json = postJson(request)
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        val finishReason = choice?.optString("finish_reason").orEmpty()
        val usage = json.optJSONObject("usage")
        diagnostic("Model: $CHAT_MODEL; finish_reason=${finishReason.ifBlank { "missing" }}; tokens: prompt=${usage?.optInt("prompt_tokens") ?: "unknown"}, completion=${usage?.optInt("completion_tokens") ?: "unknown"}.")
        if (requireComplete && finishReason != "stop") {
            throw OpenAiException("Incomplete model response (finish_reason=${finishReason.ifBlank { "missing" }}). Nothing was imported; try again.")
        }
        return choice?.optJSONObject("message")?.optString("content")?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Lightweight auth check — GET /v1/models (limit 1). */
    fun validateApiKey() {
        val request = Request.Builder()
            .url("$API_BASE/models?limit=1")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        val json = getJson(request)
        if (json.optJSONArray("data") == null) {
            throw OpenAiException("OpenAI did not confirm API key")
        }
    }

    private fun getJson(request: Request): JSONObject {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw openAiHttpError(response.code, body)
            }
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        }
    }

    private fun postJson(request: Request): JSONObject {
        val started = System.nanoTime()
        httpClient.newCall(request).execute().use { response ->
            diagnostic("OpenAI HTTP ${response.code}; elapsed ${(System.nanoTime() - started) / 1_000_000} ms; request_id=${response.header("x-request-id") ?: "unavailable"}.")
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw openAiHttpError(response.code, body)
            }
            return JSONObject(body)
        }
    }

    private fun openAiHttpError(code: Int, body: String): OpenAiException {
        val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: body.take(200)
        return OpenAiException("OpenAI HTTP $code: $detail")
    }

    companion object {
        private const val API_BASE = "https://api.openai.com/v1"
        private const val CHAT_MODEL = "gpt-4o-mini"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}

class OpenAiException(message: String) : Exception(message)

class ChartAssistantService(
    private val openAiClient: OpenAiClient,
) {
    fun searchWeb(query: String): List<SearchResult> = WebSearchService.search(query)

    suspend fun fetchAndExtractChartBatch(
        results: List<SearchResult>,
        intent: ChartIntent,
    ): ChartDraft {
        val candidates = coroutineScope {
            results.map { result ->
                async(Dispatchers.IO) {
                    runCatching {
                        val text = PageFetcher.fetchText(result.url) ?: error("Could not fetch page")
                        ChartCandidatePage(result, PageFetcher.withSearchSnippet(result.snippet, text).take(8_000))
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
        if (candidates.isEmpty()) throw ChartAssistantException("Could not fetch any search result pages")
        val draft = openAiClient.extractChartFromCandidates(candidates, intent)
            ?: throw ChartAssistantException("Could not extract chart from search results")
        return draft.copy(sourceUrl = candidates.first().result.url)
    }

    fun fetchAndExtractChart(
        result: SearchResult,
        intent: ChartIntent,
    ): ChartDraft {
        val pageText = PageFetcher.fetchText(result.url)
            ?: throw ChartAssistantException("Could not fetch page")
        val draft = openAiClient.extractChart(
            pageText = PageFetcher.withSearchSnippet(result.snippet, pageText),
            songTitle = intent.songTitle,
            artist = intent.artist,
            sourceUrl = result.url,
            searchMode = intent.searchMode,
        ) ?: throw ChartAssistantException("Could not extract chart from page")
        return draft.copy(sourceUrl = result.url)
    }
}

data class ChartCandidatePage(
    val result: SearchResult,
    val text: String,
)

class ChartAssistantException(message: String) : Exception(message)

private fun ChartDraft.withoutChords(): ChartDraft? = copy(
    sourceKey = null,
    key = null,
    capo = null,
    sections = sections.mapNotNull { section ->
        val lines = section.lines
            .map { BRACKETED_CHORD.replace(it, "").trim() }
            .filter { it.isNotEmpty() && !isChordOnlyLine(it) }
        section.copy(lines = lines).takeIf { it.lines.isNotEmpty() }
    },
).takeIf { it.sections.isNotEmpty() }

private fun isChordOnlyLine(line: String): Boolean =
    line.split(Regex("\\s+")).all { CHORD_TOKEN.matches(it) }

private val BRACKETED_CHORD = Regex("""<[^>]+>""")
private val CHORD_TOKEN = Regex(
    """[A-G](?:#|b)?(?:maj|min|m|dim|aug|sus|add)?\d*(?:/[A-G](?:#|b)?)?""",
)
