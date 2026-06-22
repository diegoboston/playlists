package com.playlists.app.remote

import android.content.Context
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

object CloudflareTunnel {
    private val URL_PATTERN = Pattern.compile("""https://[a-z0-9-]+\.trycloudflare\.com""")

    private var process: Process? = null
    private var outputThread: Thread? = null

    fun start(context: Context, localPort: Int, timeoutSeconds: Long = 60): Result<String> {
        stop()
        val binary = ensureBinary(context)
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
            val latch = CountDownLatch(1)
            outputThread = Thread({
                proc.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val matcher = URL_PATTERN.matcher(line)
                        if (matcher.find()) {
                            urlRef.set(matcher.group())
                            latch.countDown()
                            break
                        }
                    }
                }
            }, "cloudflared-output").apply { isDaemon = true; start() }

            if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                stop()
                return Result.failure(IllegalStateException("Cloudflare tunnel timed out"))
            }
            val url = urlRef.get()
                ?: run {
                    stop()
                    return Result.failure(IllegalStateException("Cloudflare tunnel did not return a URL"))
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
        process?.destroy()
        process?.waitFor(3, TimeUnit.SECONDS)
        process?.destroyForcibly()
        process = null
    }

    private fun ensureBinary(context: Context): File {
        val dest = File(context.filesDir, "cloudflared")
        if (dest.exists() && dest.length() > 0) {
            dest.setExecutable(true, false)
            return dest
        }
        context.assets.open("cloudflared").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.setExecutable(true, false)
        return dest
    }
}
