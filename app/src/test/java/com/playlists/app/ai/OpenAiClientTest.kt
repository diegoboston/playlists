package com.playlists.app.ai

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject

class OpenAiClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun validateApiKey_successOn200() {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        val client = clientForServer()
        client.validateApiKey()
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("/models"))
        assertTrue(request.getHeader("Authorization")!!.contains("sk-test"))
    }

    @Test
    fun validateApiKey_failsOn401() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Invalid API key"}}"""),
        )
        val client = clientForServer()
        val error = assertThrows(OpenAiException::class.java) {
            client.validateApiKey()
        }
        assertTrue(error.message!!.contains("401"))
    }

    @Test
    fun validateApiKey_failsOn200WithoutModelsList() {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val client = clientForServer()
        val error = assertThrows(OpenAiException::class.java) {
            client.validateApiKey()
        }
        assertTrue(error.message!!.contains("did not confirm"))
    }

    @Test
    fun extractTitleFromImage_readsTitleJson() {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"{\"title\":\"Amazing Grace\"}"}}]}""",
            ),
        )
        val client = clientForServer()
        val title = client.extractTitleFromImage("abc".toByteArray(), "image/jpeg")
        assertEquals("Amazing Grace", title)
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("/chat/completions"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("image_url"))
        assertTrue(body.contains("data:image/jpeg;base64,"))
        assertTrue(body.contains("YWJj"))
        assertTrue(body.contains(AiPrompts.EXTRACT_TITLE_FROM_IMAGE_USER))
        assertTrue(body.contains("most prominent printed song title"))
    }

    @Test
    fun extractTitleFromImage_emptyWhenModelFindsNone() {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"{\"title\":\"\"}"}}]}""",
            ),
        )
        val client = clientForServer()
        assertEquals("", client.extractTitleFromImage("x".toByteArray()))
    }

    @Test
    fun extractTitleFromImage_failsOn401() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Invalid API key"}}"""),
        )
        val client = clientForServer()
        val error = assertThrows(OpenAiException::class.java) {
            client.extractTitleFromImage("x".toByteArray())
        }
        assertTrue(error.message!!.contains("401"))
    }

    @Test
    fun extractChart_parsesLineOrientedChart() {
        val chart = """
            TITLE: X Colpa Di Chi?
            ARTIST: Zucchero

            [Verse 1]
            Funky gallo, come sono bello stamattina
        """.trimIndent()
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"finish_reason":"stop","message":{"content":${JSONObject.quote(chart)}}}]}""",
            ),
        )
        val draft = clientForServer().extractChart(
            pageText = "Funky gallo, come sono bello stamattina",
            songTitle = "X Colpa di chi",
            artist = "Zucchero",
            sourceUrl = "https://www.lyrics.com/example",
            searchMode = ChartSearchMode.LyricsOnly,
        )
        assertEquals("X Colpa Di Chi?", draft!!.title)
        assertEquals("Zucchero", draft.artist)
        assertEquals("Funky gallo, come sono bello stamattina", draft.sections[0].lines[0])
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("TITLE:"))
        assertFalse(body.contains("json_object"))
    }

    @Test
    fun extractChart_keepsCompleteLinesWhenTruncated() {
        val truncated = """
            TITLE: X Colpa Di Chi?
            ARTIST: Zucchero

            [Verse 1]
            Funky gallo, come sono bello stamattina

            [Chorus
        """.trimIndent()
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"finish_reason":"content_filter","message":{"content":${JSONObject.quote(truncated)}}}]}""",
            ),
        )
        val draft = clientForServer().extractChart(
            pageText = "Funky gallo, come sono bello stamattina",
            songTitle = "X Colpa di chi",
            artist = "Zucchero",
            sourceUrl = "https://www.lyrics.com/example",
            searchMode = ChartSearchMode.LyricsOnly,
        )
        assertEquals("X Colpa Di Chi?", draft!!.title)
        assertEquals("Funky gallo, come sono bello stamattina", draft.sections[0].lines[0])
    }

    @Test
    fun extractPastedTextSendsRawContentAndDoesNotInventASourceUrl() {
        val raw = "Advertisement\nTest song by Artist\n[Verse]\n[C]Hello\nSubscribe"
        val chart = """{"title":"Test song","artist":"Artist","key":"C","lineKinds":["clutter","metadata","section","lyrics","clutter"]}"""
        repeat(2) {
            server.enqueue(MockResponse().setBody(JSONObject().put("choices", org.json.JSONArray()
                .put(JSONObject().put("finish_reason", "stop").put("message", JSONObject().put("content", chart)))).toString()))
        }
        val client = clientForServer()
        val chords = client.extractPastedText(raw, ChartSearchMode.ChordsAndLyrics)!!
        assertEquals("<C>Hello", chords.sections.first().lines.first())
        assertEquals(null, chords.sourceUrl)
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        val messages = JSONObject(request.body.readUtf8()).getJSONArray("messages")
        assertEquals(PastedSongParser.lines(raw), org.json.JSONArray(messages.getJSONObject(1).getString("content")).let { a -> (0 until a.length()).map { a.getString(it) } })
        assertTrue(messages.getJSONObject(0).getString("content").contains("never deduplicate"))
        val lyrics = client.extractPastedText(raw, ChartSearchMode.LyricsOnly)!!
        assertEquals("Hello", lyrics.sections.first().lines.first())
        assertEquals(null, lyrics.key)
        server.takeRequest()
        assertThrows(IllegalArgumentException::class.java) {
            client.extractPastedText("x".repeat(30_001), ChartSearchMode.LyricsOnly)
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun pastedTextRejectsTruncatedClassificationAndLogsFinishReason() {
        for (reason in listOf("length", "content_filter", "")) {
            server.enqueue(MockResponse().setBody(JSONObject().put("choices", org.json.JSONArray()
                .put(JSONObject().put("finish_reason", reason).put("message", JSONObject().put("content", "{}")))).toString()))
            val logs = mutableListOf<String>()
            val error = assertThrows(OpenAiException::class.java) {
                clientForServer(logs::add).extractPastedText("Hello", ChartSearchMode.LyricsOnly)
            }
            assertTrue(error.message!!.contains("Nothing was imported"))
            assertTrue(logs.any { it.contains("finish_reason=") })
            assertFalse(logs.any { it.contains("sk-test") })
        }
    }

    private fun clientForServer(log: (String) -> Unit = {}): OpenAiClient {
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.newBuilder()
                    .scheme("http")
                    .host(server.hostName)
                    .port(server.port)
                    .build()
                chain.proceed(chain.request().newBuilder().url(url).build())
            }
            .build()
        return OpenAiClient("sk-test", http, log)
    }
}
