package com.playlists.app.ai

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
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
