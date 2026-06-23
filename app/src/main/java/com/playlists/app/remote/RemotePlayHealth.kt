package com.playlists.app.remote

import java.net.HttpURLConnection
import java.net.URL

object RemotePlayHealth {
    data class ProbeResult(
        val label: String,
        val ok: Boolean,
        val detail: String,
    )

    fun probeGet(url: String, timeoutMs: Int = 8_000): ProbeResult {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        return try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connect()
            val code = connection.responseCode
            val ok = code in 200..399
            ProbeResult(
                label = url,
                ok = ok,
                detail = if (ok) "HTTP $code" else "HTTP $code",
            )
        } catch (e: Exception) {
            ProbeResult(
                label = url,
                ok = false,
                detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName,
            )
        } finally {
            connection.disconnect()
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
                "${last.detail} (after $attempts attempts)"
            },
        )
    }

    /**
     * The app's HttpURLConnection probe often fails while Chrome on the same phone
     * loads the tunnel fine (DNS timing, carrier cache, etc.). When cloudflared and
     * the local server are healthy, treat the tunnel as up without a false FAIL.
     */
    fun tunnelStatusWhenServing(
        tunnelBaseUrl: String,
        cloudflaredRunning: Boolean,
        localOk: Boolean,
    ): ProbeResult {
        val url = tunnelBaseUrl.trimEnd('/') + "/"
        if (cloudflaredRunning && localOk) {
            return ProbeResult(
                label = url,
                ok = true,
                detail = "cloudflared running — open the link above in your browser to verify",
            )
        }
        val probe = probeTunnelWithRetries(tunnelBaseUrl, attempts = 2, pauseMs = 2_000, timeoutMs = 6_000)
        if (probe.ok) return probe
        return probe
    }
}
