package com.playlists.app.remote

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

object RemotePlayHealth {
    data class ProbeResult(
        val label: String,
        val ok: Boolean,
        val detail: String,
    )

    data class UrlProbe(val label: String, val result: ProbeResult)

    /** Check every advertised address independently, including stable redirects and LAN interfaces. */
    fun probeUrls(entries: List<RemotePlayUrlEntry>): List<UrlProbe> = runBlocking {
        entries.distinctBy { it.url }.map { entry ->
            async(Dispatchers.IO) {
                UrlProbe(entry.label, probeGet(entry.url, timeoutMs = 4_000))
            }
        }.awaitAll()
    }

    fun probeGet(url: String, timeoutMs: Int = 8_000): ProbeResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.connect()
            val code = connection.responseCode
            val ok = code in 200..299
            ProbeResult(
                label = url,
                ok = ok,
                detail = "HTTP $code",
            )
        } catch (e: Exception) {
            ProbeResult(
                label = url,
                ok = false,
                detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName,
            )
        } finally {
            connection?.disconnect()
        }
    }

    fun probeTunnelWithRetries(
        baseUrl: String,
        attempts: Int = 4,
        pauseMs: Long = 1_500,
        timeoutMs: Int = 6_000,
    ): ProbeResult {
        val url = baseUrl.trimEnd('/') + "/"
        var last = probeGet(url, timeoutMs)
        var attempt = 1
        while (!last.ok && attempt < attempts) {
            Thread.sleep(pauseMs)
            last = probeGet(url, timeoutMs)
            attempt++
        }
        return last.copy(
            detail = if (last.ok) {
                "${last.detail} (attempt $attempt/$attempts)"
            } else {
                "${last.detail} (after $attempt attempts)"
            },
        )
    }

    /** Process liveness does not prove a connector is attached to Cloudflare's edge. */
    fun tunnelStatusWhenServing(
        tunnelBaseUrl: String,
        cloudflaredRunning: Boolean,
        localOk: Boolean,
    ): ProbeResult {
        val url = tunnelBaseUrl.trimEnd('/') + "/"
        if (!cloudflaredRunning || !localOk) {
            return ProbeResult(
                label = url,
                ok = false,
                detail = if (!cloudflaredRunning) "cloudflared is not running" else "Local HTTP server is not responding",
            )
        }
        return probeTunnelWithRetries(tunnelBaseUrl, attempts = 2, pauseMs = 1_000, timeoutMs = 4_000)
    }
}
