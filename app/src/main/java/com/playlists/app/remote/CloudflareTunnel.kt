package com.playlists.app.remote

import android.content.Context
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

object CloudflareTunnel {
    internal const val CLOUDFLARED_LIB_NAME = "libcloudflared.so"

    private val URL_PATTERN = Pattern.compile("""https://[a-z0-9-]+\.trycloudflare\.com""")

    /** Quick-tunnel API host — not the public URL visitors should open. */
    private const val QUICK_TUNNEL_API_HOST = "api.trycloudflare.com"

    internal fun extractPublicTunnelUrl(line: String): String? {
        val matcher = URL_PATTERN.matcher(line)
        while (matcher.find()) {
            val url = matcher.group()
            if (!url.contains("://$QUICK_TUNNEL_API_HOST")) {
                return url
            }
        }
        return null
    }

    @Volatile private var process: Process? = null
    private var outputThread: Thread? = null
    private var waitThread: Thread? = null
    private val logLines = Collections.synchronizedList(mutableListOf<String>())
    private var lastExitCode: Int? = null

    private const val MAX_LOG_LINES = 80

    internal fun isRegisteredConnection(line: String): Boolean =
        line.contains("Registered tunnel connection", ignoreCase = true)

    internal fun isStartupReady(publicUrl: String?, connectionRegistered: Boolean): Boolean =
        publicUrl != null && connectionRegistered

    fun isRunning(): Boolean = process?.isAlive == true

    fun lastExitCode(): Int? = lastExitCode

    fun recentLogs(): String = synchronized(logLines) {
        logLines.takeLast(40).joinToString("\n")
    }

    internal fun clearLogs() {
        synchronized(logLines) { logLines.clear() }
        lastExitCode = null
    }

    private fun appendLog(line: String) {
        logLines.add(line)
        while (logLines.size > MAX_LOG_LINES) {
            logLines.removeAt(0)
        }
    }

    fun binaryPath(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, CLOUDFLARED_LIB_NAME)

    fun start(context: Context, localPort: Int, timeoutSeconds: Long = 60): Result<String> {
        stop()
        clearLogs()
        val binaryResult = ensureBinary(context)
        if (binaryResult.isFailure) {
            return Result.failure(
                binaryResult.exceptionOrNull() ?: IllegalStateException("cloudflared missing"),
            )
        }
        val binary = binaryResult.getOrThrow()
        val command = listOf(
            binary.absolutePath,
            "tunnel",
            "--no-autoupdate",
            "--url",
            "http://127.0.0.1:$localPort",
        )
        return try {
            val proc = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            process = proc
            val urlRef = AtomicReference<String>()
            val connectionRegistered = AtomicBoolean(false)
            val exitCode = AtomicInteger(-1)
            val outputLines = Collections.synchronizedList(mutableListOf<String>())
            val latch = CountDownLatch(1)
            outputThread = Thread({
                // Keep reading until cloudflared exits — closing stdout early sends SIGPIPE
                // and kills the tunnel (Cloudflare 530 on the public URL).
                proc.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        synchronized(outputLines) {
                            outputLines.add(line)
                            if (outputLines.size > MAX_LOG_LINES) outputLines.removeAt(0)
                        }
                        if (process === proc) appendLog(line)
                        if (urlRef.get() == null) {
                            extractPublicTunnelUrl(line)?.let { url ->
                                urlRef.set(url)
                            }
                        }
                        if (isRegisteredConnection(line)) connectionRegistered.set(true)
                        if (isStartupReady(urlRef.get(), connectionRegistered.get())) latch.countDown()
                    }
                }
            }, "cloudflared-output").apply { isDaemon = true; start() }
            waitThread = Thread({
                try {
                    val code = proc.waitFor()
                    exitCode.set(code)
                    if (process === proc) lastExitCode = code
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    latch.countDown()
                }
            }, "cloudflared-wait").apply { isDaemon = true; start() }

            if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                stop()
                return Result.failure(
                    tunnelFailure("Cloudflare tunnel did not establish a connection within ${timeoutSeconds}s", outputLines),
                )
            }
            val url = urlRef.get()
            if (!isStartupReady(url, connectionRegistered.get()) || !proc.isAlive) {
                stop()
                val code = exitCode.get()
                val prefix = when {
                    code >= 0 -> "cloudflared exited with code $code"
                    url == null -> "Cloudflare did not return a public tunnel URL"
                    !connectionRegistered.get() -> "Cloudflare assigned a URL but cloudflared never established a connection"
                    else -> "cloudflared stopped before tunnel startup completed"
                }
                return Result.failure(tunnelFailure(prefix, outputLines))
            }
            Result.success(requireNotNull(url))
        } catch (e: Exception) {
            stop()
            Result.failure(e)
        }
    }

    fun stop() {
        val proc = process
        process = null
        val outThread = outputThread
        val waitThr = waitThread
        outputThread = null
        waitThread = null
        outThread?.interrupt()
        waitThr?.interrupt()
        if (proc == null) return
        proc.destroy()
        Thread({
            try {
                if (!proc.waitFor(3, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                }
            } catch (_: InterruptedException) {
                proc.destroyForcibly()
                Thread.currentThread().interrupt()
            }
        }, "cloudflared-stop").apply { isDaemon = true; start() }
    }

    private fun ensureBinary(context: Context): Result<File> {
        val binary = binaryPath(context)
        if (binary.exists() && binary.length() > 0) {
            return Result.success(binary)
        }
        return Result.failure(
            IllegalStateException(
                "Bundled cloudflared missing from APK.\n" +
                    "Expected: ${binary.absolutePath}\n" +
                    "Run scripts/fetch-cloudflared.sh and rebuild.",
            ),
        )
    }

    private fun tunnelFailure(prefix: String, outputLines: List<String>): IllegalStateException {
        val output = synchronized(outputLines) { outputLines.takeLast(MAX_LOG_LINES).joinToString("\n").trim() }
        val detail = if (output.isNotEmpty()) "$prefix\n\n$output" else prefix
        return IllegalStateException(detail)
    }
}
