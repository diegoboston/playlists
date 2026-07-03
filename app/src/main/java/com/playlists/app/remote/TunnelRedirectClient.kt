package com.playlists.app.remote

import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Publishes the live quick-tunnel URL to the Cloudflare Worker stable redirect (KV).
 */
object TunnelRedirectClient {

    const val WORKER_NAME = "play"

    private val TUNNEL_URL_PATTERN = Pattern.compile("""https://[a-z0-9-]+\.trycloudflare\.com""")
    private const val QUICK_TUNNEL_API_HOST = "api.trycloudflare.com"

    fun buildWorkerBaseUrl(workersSubdomain: String): String {
        val sub = workersSubdomain.trim().lowercase()
        return "https://$WORKER_NAME.$sub.workers.dev"
    }

    fun normalizeTunnelBaseUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val matcher = TUNNEL_URL_PATTERN.matcher(trimmed)
        while (matcher.find()) {
            val url = matcher.group()
            if (!url.contains("://$QUICK_TUNNEL_API_HOST")) {
                return url
            }
        }
        return null
    }

    fun validateWriteSecret(workerBaseUrl: String, secret: String): Result<Unit> {
        val base = workerBaseUrl.trim().trimEnd('/')
        if (base.isEmpty()) {
            return Result.failure(IllegalArgumentException("Worker base URL is empty"))
        }
        val token = secret.trim()
        if (token.isEmpty()) {
            return Result.failure(IllegalArgumentException("Write secret is empty"))
        }

        val conn = (URL("$base/register").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            when (val code = conn.responseCode) {
                401 -> Result.failure(IllegalStateException("Unauthorized"))
                in 200..299 -> Result.success(Unit)
                in 400..499 -> Result.success(Unit)
                else -> {
                    val detail = (conn.errorStream ?: conn.inputStream)
                        .bufferedReader()
                        .readText()
                        .take(200)
                        .ifBlank { "HTTP $code" }
                    Result.failure(IllegalStateException(detail))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }

    /** GET /url — registered tunnel base, or null if none. */
    fun registeredTunnelUrl(workerBaseUrl: String): Result<String?> {
        val base = workerBaseUrl.trim().trimEnd('/')
        if (base.isEmpty()) {
            return Result.failure(IllegalArgumentException("Worker base URL is empty"))
        }
        val conn = (URL("$base/url").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return try {
            when (val code = conn.responseCode) {
                in 200..299 -> {
                    val body = conn.inputStream.bufferedReader().readText().trim()
                    Result.success(body.takeIf { it.isNotEmpty() })
                }
                else -> {
                    val detail = (conn.errorStream ?: conn.inputStream)
                        .bufferedReader()
                        .readText()
                        .take(200)
                        .ifBlank { "HTTP $code" }
                    Result.failure(IllegalStateException(detail))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }

    fun verifyRegisteredTunnel(workerBaseUrl: String, expectedTunnelUrl: String): Result<Unit> {
        val expected = normalizeTunnelBaseUrl(expectedTunnelUrl)
            ?: return Result.failure(IllegalArgumentException("Invalid tunnel URL"))
        return registeredTunnelUrl(workerBaseUrl).fold(
            onSuccess = { registered ->
                val normalizedRegistered = registered?.let { normalizeTunnelBaseUrl(it) }
                when {
                    normalizedRegistered == null ->
                        Result.failure(IllegalStateException("No tunnel registered on worker"))
                    normalizedRegistered != expected ->
                        Result.failure(IllegalStateException("Worker tunnel mismatch"))
                    else -> Result.success(Unit)
                }
            },
            onFailure = { Result.failure(it) },
        )
    }

    fun publish(workerBaseUrl: String, secret: String, tunnelUrl: String): Result<Unit> {
        val base = workerBaseUrl.trim().trimEnd('/')
        if (base.isEmpty()) {
            return Result.failure(IllegalArgumentException("Worker base URL is empty"))
        }
        val normalizedTunnel = normalizeTunnelBaseUrl(tunnelUrl)
            ?: return Result.failure(IllegalArgumentException("Invalid tunnel URL"))
        val token = secret.trim()
        if (token.isEmpty()) {
            return Result.failure(IllegalArgumentException("Write secret is empty"))
        }

        val body = """{"url":"$normalizedTunnel"}"""
        val conn = (URL("$base/register").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                Result.success(Unit)
            } else {
                val detail = (conn.errorStream ?: conn.inputStream)
                    .bufferedReader()
                    .readText()
                    .take(200)
                    .ifBlank { "HTTP $code" }
                Result.failure(IllegalStateException(detail))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }
}
