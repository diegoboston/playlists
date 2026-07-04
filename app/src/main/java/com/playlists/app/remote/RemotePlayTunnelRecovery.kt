package com.playlists.app.remote

/**
 * Restarts cloudflared when it dies mid-session and re-publishes the stable URL (KV).
 */
internal object RemotePlayTunnelRecovery {
    private const val CHECK_INTERVAL_MS = 30_000L
    private const val MAX_RESTARTS = 3
    private const val RESTART_WINDOW_MS = 10 * 60_000L

    private var watchdogThread: Thread? = null
    private val restartTimestamps = mutableListOf<Long>()
    private val lock = Any()

    fun startWatchdog() {
        synchronized(lock) {
            stopWatchdogLocked()
            watchdogThread = Thread({
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        Thread.sleep(CHECK_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                    PlayRemoteController.tryRecoverCloudflareTunnel()
                }
            }, "remote-tunnel-watchdog").apply {
                isDaemon = true
                start()
            }
        }
    }

    fun stopWatchdog() {
        synchronized(lock) {
            stopWatchdogLocked()
        }
    }

    internal fun recordRestartAttempt(nowMs: Long): Boolean {
        synchronized(lock) {
            val windowStart = nowMs - RESTART_WINDOW_MS
            restartTimestamps.removeAll { it < windowStart }
            if (restartTimestamps.size >= MAX_RESTARTS) return false
            restartTimestamps.add(nowMs)
            return true
        }
    }

    private fun stopWatchdogLocked() {
        watchdogThread?.interrupt()
        watchdogThread = null
        restartTimestamps.clear()
    }
}

internal fun shouldAttemptTunnelRecovery(
    mode: RemotePlayMode,
    cloudflaredRunning: Boolean,
    localProbeOk: Boolean,
    serverAlive: Boolean,
): Boolean {
    if (!mode.usesCloudflareTunnel()) return false
    if (!serverAlive || !localProbeOk) return false
    return !cloudflaredRunning
}
