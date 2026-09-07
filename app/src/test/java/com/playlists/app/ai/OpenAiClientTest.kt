package com.playlists.app.ai

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
    fun flattenAndCleanupImage_readsB64Json() {
        val png = java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))
        server.enqueue(
            MockResponse().setBody("""{"data":[{"b64_json":"$png"}]}"""),
        )
        val client = clientForServer()
        val bytes = client.flattenAndCleanupImage("abc".toByteArray())
        assertEquals(listOf<Byte>(1, 2, 3, 4), bytes.toList())
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("/images/edits"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("gpt-image-1"))
        assertTrue(body.contains(AiPrompts.FLATTEN_IMAGE))
    }

    @Test
    fun flattenAndCleanupImage_failsOn401() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Invalid API key"}}"""),
        )
        val client = clientForServer()
        val error = assertThrows(OpenAiException::class.java) {
            client.flattenAndCleanupImage("x".toByteArray())
        }
        assertTrue(error.message!!.contains("401"))
    }

    private fun clientForServer(): OpenAiClient {
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
        return OpenAiClient("sk-test", http)
    }
}
