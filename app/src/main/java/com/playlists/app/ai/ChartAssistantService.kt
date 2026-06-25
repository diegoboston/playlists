package com.playlists.app.ai

import com.playlists.app.find.PageFetcher
import com.playlists.app.find.SearchResult
import com.playlists.app.find.WebSearchService
import com.playlists.app.render.ChartPdfRenderer
import com.playlists.app.render.ChordTransposer
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
    ): ChartIntent? {
        val system = """
            You parse voice commands for a chord-chart assistant app.
            Return JSON only with fields:
            action (always "find_chart" for now),
            songTitle (required),
            artist (optional),
            key (optional target key like G, Am, F#),
            playlistName (optional).
            Context: $playlistsContextJson
        """.trimIndent()
        val content = chatJson(system, transcript) ?: return null
        return AiJsonHelper.parseObject(content)?.let { ChartIntent.fromJson(it, transcript) }
    }

    fun extractChart(
        pageText: String,
        songTitle: String,
        artist: String?,
        targetKey: String?,
        sourceUrl: String,
    ): ChartDraft? {
        val keyInstruction = targetKey?.let { "Target key for output: $it." }.orEmpty()
        val system = """
            Extract a chord chart with lyrics from the web page text.
            Return JSON only:
            {
              "title": "...",
              "artist": "...",
              "sourceKey": "key on page if stated",
              "key": "same as sourceKey unless transposing",
              "capo": null or string,
              "columns": 1,
              "sections": [{"label":"Verse 1","lines":["G  C  G","lyrics with chords above or inline"]}],
              "notes": "optional",
              "sourceUrl": "$sourceUrl"
            }
            Include chord symbols with lyric lines. $keyInstruction
            Song requested: $songTitle ${artist.orEmpty()}
        """.trimIndent()
        val content = chatJson(system, pageText.take(30_000)) ?: return null
        return AiJsonHelper.parseObject(content)?.let { ChartDraft.fromJson(it) }
    }

    private fun chatJson(system: String, user: String): String? {
        val payload = JSONObject()
            .put("model", CHAT_MODEL)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
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
        getJson(request)
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
        var draft = openAiClient.extractChart(
            pageText = pageText,
            songTitle = intent.songTitle,
            artist = intent.artist,
            targetKey = intent.key,
            sourceUrl = result.url,
        ) ?: throw ChartAssistantException("Could not extract chart from page")
        draft = draft.copy(sourceUrl = result.url)
        if (!intent.key.isNullOrBlank()) {
            draft = ChordTransposer.transpose(draft, intent.key)
        }
        val pdf = ChartPdfRenderer.render(draft)
        return draft to pdf
    }
}

class ChartAssistantException(message: String) : Exception(message)
