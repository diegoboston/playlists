package com.playlists.app.remote

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlayHealthTest {
    @Test
    fun checksEveryDistinctUrlAndKeepsIndividualFailures() {
        MockWebServer().use { healthy ->
            MockWebServer().use { broken ->
                healthy.enqueue(MockResponse().setResponseCode(200))
                broken.enqueue(MockResponse().setResponseCode(530))
                val goodUrl = healthy.url("/?playlist=42").toString()
                val badUrl = broken.url("/").toString()
                val probes = RemotePlayHealth.probeUrls(listOf(
                    RemotePlayUrlEntry("LAN", goodUrl),
                    RemotePlayUrlEntry("Cloudflare", badUrl),
                    RemotePlayUrlEntry("Duplicate", goodUrl),
                    RemotePlayUrlEntry("Invalid", "invalid-url"),
                ))
                assertEquals(listOf("LAN", "Cloudflare", "Invalid"), probes.map { it.label })
                assertTrue(probes[0].result.ok)
                assertFalse(probes[1].result.ok)
                assertFalse(probes[2].result.ok)
                assertEquals(goodUrl, probes[0].result.label)
                assertEquals("/?playlist=42", healthy.takeRequest().path)
                assertEquals(1, healthy.requestCount)
                assertEquals(1, broken.requestCount)
            }
        }
    }

    @Test
    fun runningProcessDoesNotHidePublicCloudflareError() {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setResponseCode(530).setBody("Cloudflare error 1033")) }
            val status = RemotePlayHealth.tunnelStatusWhenServing(server.url("/").toString(), true, true)
            assertFalse(status.ok)
            assertTrue(status.detail.contains("HTTP 530"))
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun tunnelIsOkOnlyAfterPublicHttpSuccess() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("PIN page"))
            val status = RemotePlayHealth.tunnelStatusWhenServing(server.url("/").toString(), true, true)
            assertTrue(status.ok)
            assertTrue(status.detail.contains("HTTP 200"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun publicProbeCanRecoverOnRetry() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(530))
            server.enqueue(MockResponse().setResponseCode(200))
            val status = RemotePlayHealth.probeTunnelWithRetries(server.url("/").toString(), attempts = 2, pauseMs = 0)
            assertTrue(status.ok)
            assertTrue(status.detail.contains("attempt 2/2"))
        }
    }

    @Test
    fun processStoppedDoesNotClaimPublicSuccessOrProbeOldUrl() {
        MockWebServer().use { server ->
            val status = RemotePlayHealth.tunnelStatusWhenServing(server.url("/").toString(), false, true)
            assertFalse(status.ok)
            assertTrue(status.detail.contains("not running"))
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun localServerFailureDoesNotClaimTunnelSuccess() {
        MockWebServer().use { server ->
            val status = RemotePlayHealth.tunnelStatusWhenServing(server.url("/").toString(), true, false)
            assertFalse(status.ok)
            assertTrue(status.detail.contains("Local HTTP server"))
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun redirectToAnErrorIsNotHealthy() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/error")))
            server.enqueue(MockResponse().setResponseCode(530))
            val status = RemotePlayHealth.probeGet(server.url("/").toString())
            assertFalse(status.ok)
            assertTrue(status.detail.contains("HTTP 530"))
        }
    }
}
