package com.playlists.app.ai

import com.playlists.app.find.PageFetcher
import com.playlists.app.find.SearchResult
import com.playlists.app.find.WebSearchService
import com.playlists.app.render.ChartPdfRenderer
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

class OpenAiClient(
    private val apiKey: String,
    private val httpClient: OkHttpClient = defaultClient(),
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
        ) ?: return null
        return AiJsonHelper.parseObject(content)?.let { ChartDraft.fromJson(it) }
            ?.let { draft -> if (lyricsOnly) draft.withoutChords() else draft }
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

    /**
     * Deskew / flatten a photo of a chart into a clean document scan.
     * Returns image bytes (JPEG or PNG) from the Images API.
     */
    fun flattenAndCleanupImage(imageBytes: ByteArray, mimeType: String = "image/jpeg"): ByteArray {
        val filename = if (mimeType.contains("png")) "page.png" else "page.jpg"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", IMAGE_EDIT_MODEL)
            .addFormDataPart("prompt", AiPrompts.FLATTEN_IMAGE)
            .addFormDataPart("n", "1")
            .addFormDataPart(
                "image",
                filename,
                imageBytes.toRequestBody(mimeType.toMediaType()),
            )
            .build()
        val request = Request.Builder()
            .url("$API_BASE/images/edits")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        val json = postJson(request)
        val data = json.optJSONArray("data")?.optJSONObject(0)
            ?: throw OpenAiException("OpenAI did not return a cleaned image")
        val b64 = data.optString("b64_json").trim()
        if (b64.isNotEmpty()) {
            return java.util.Base64.getDecoder().decode(b64)
        }
        val url = data.optString("url").trim()
        if (url.isEmpty()) {
            throw OpenAiException("OpenAI did not return a cleaned image")
        }
        val download = Request.Builder().url(url).get().build()
        httpClient.newCall(download).execute().use { response ->
            val bytes = response.body?.bytes()
            if (!response.isSuccessful || bytes == null || bytes.isEmpty()) {
                throw OpenAiException("OpenAI cleaned image download failed")
            }
            return bytes
        }
    }

    private fun chatJson(
        system: String,
        userContent: Any,
        maxTokens: Int? = null,
        temperature: Double? = null,
    ): String? {
        val payload = JSONObject()
            .put("model", CHAT_MODEL)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", userContent)),
            )
        if (maxTokens != null) payload.put("max_tokens", maxTokens)
        if (temperature != null) payload.put("temperature", temperature)
        val request = Request.Builder()
            .url("$API_BASE/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val json = postJson(request)
        return json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
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
        httpClient.newCall(request).execute().use { response ->
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
        private const val IMAGE_EDIT_MODEL = "gpt-image-1"

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

    fun fetchAndBuildChart(
        result: SearchResult,
        intent: ChartIntent,
    ): Pair<ChartDraft, ByteArray> {
        val pageText = PageFetcher.fetchText(result.url)
            ?: throw ChartAssistantException("Could not fetch page")
        val draft = openAiClient.extractChart(
            pageText = pageText,
            songTitle = intent.songTitle,
            artist = intent.artist,
            sourceUrl = result.url,
            searchMode = intent.searchMode,
        ) ?: throw ChartAssistantException("Could not extract chart from page")
        val pdf = ChartPdfRenderer.render(draft.copy(sourceUrl = result.url))
        return draft.copy(sourceUrl = result.url) to pdf
    }
}

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
