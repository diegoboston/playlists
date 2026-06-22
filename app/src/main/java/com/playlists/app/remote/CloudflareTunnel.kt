package com.playlists.app.remote

import android.content.Context
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

    private var process: Process? = null
    private var outputThread: Thread? = null
    private var waitThread: Thread? = null

    fun binaryPath(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, CLOUDFLARED_LIB_NAME)

    fun start(context: Context, localPort: Int, timeoutSeconds: Long = 60): Result<String> {
        stop()
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
            val exitCode = AtomicInteger(-1)
            val outputLines = Collections.synchronizedList(mutableListOf<String>())
            val latch = CountDownLatch(1)
            outputThread = Thread({
                // Keep reading until cloudflared exits — closing stdout early sends SIGPIPE
                // and kills the tunnel (Cloudflare 530 on the public URL).
                proc.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        outputLines.add(line)
                        if (urlRef.get() == null) {
                            extractPublicTunnelUrl(line)?.let { url ->
                                urlRef.set(url)
                                latch.countDown()
                            }
                        }
                    }
                }
            }, "cloudflared-output").apply { isDaemon = true; start() }
            waitThread = Thread({
                exitCode.set(proc.waitFor())
                if (urlRef.get() == null) {
                    latch.countDown()
                }
            }, "cloudflared-wait").apply { isDaemon = true; start() }

            if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                stop()
                return Result.failure(
                    tunnelFailure("Cloudflare tunnel timed out after ${timeoutSeconds}s", outputLines),
                )
            }
            val url = urlRef.get()
            if (url == null) {
                stop()
                val code = exitCode.get()
                val prefix = if (code >= 0) {
                    "cloudflared exited with code $code"
                } else {
                    "Cloudflare tunnel did not return a URL"
                }
                return Result.failure(tunnelFailure(prefix, outputLines))
            }
            Result.success(url)
        } catch (e: Exception) {
            stop()
            Result.failure(e)
        }
    }

    fun stop() {
        outputThread?.interrupt()
        outputThread = null
        waitThread?.interrupt()
        waitThread = null
        process?.destroy()
        process?.waitFor(3, TimeUnit.SECONDS)
        process?.destroyForcibly()
        process = null
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
        val output = outputLines.joinToString("\n").trim()
        val detail = if (output.isNotEmpty()) "$prefix\n\n$output" else prefix
        return IllegalStateException(detail)
    }
}
