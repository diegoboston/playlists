package com.playlists.app.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlayTunnelRecoveryTest {
    @Test
    fun shouldAttemptTunnelRecovery_whenCloudflaredDeadButServerHealthy() {
        assertTrue(
            shouldAttemptTunnelRecovery(
                mode = RemotePlayMode.CLOUDFLARE,
                cloudflaredRunning = false,
                localProbeOk = true,
                serverAlive = true,
            ),
        )
    }

    @Test
    fun shouldAttemptTunnelRecovery_falseForLanOrHealthyTunnel() {
        assertFalse(
            shouldAttemptTunnelRecovery(
                mode = RemotePlayMode.LAN,
                cloudflaredRunning = false,
                localProbeOk = true,
                serverAlive = true,
            ),
        )
        assertFalse(
            shouldAttemptTunnelRecovery(
                mode = RemotePlayMode.CLOUDFLARE,
                cloudflaredRunning = true,
                localProbeOk = true,
                serverAlive = true,
            ),
        )
    }

    @Test
    fun shouldAttemptTunnelRecovery_falseWhenLocalServerUnhealthy() {
        assertFalse(
            shouldAttemptTunnelRecovery(
                mode = RemotePlayMode.STABLE,
                cloudflaredRunning = false,
                localProbeOk = false,
                serverAlive = true,
            ),
        )
        assertFalse(
            shouldAttemptTunnelRecovery(
                mode = RemotePlayMode.STABLE,
                cloudflaredRunning = false,
                localProbeOk = true,
                serverAlive = false,
            ),
        )
    }

    @Test
    fun recordRestartAttempt_rateLimitsWithinWindow() {
        val now = 1_000_000L
        assertTrue(RemotePlayTunnelRecovery.recordRestartAttempt(now))
        assertTrue(RemotePlayTunnelRecovery.recordRestartAttempt(now + 1_000))
        assertTrue(RemotePlayTunnelRecovery.recordRestartAttempt(now + 2_000))
        assertFalse(RemotePlayTunnelRecovery.recordRestartAttempt(now + 3_000))
    }
}
