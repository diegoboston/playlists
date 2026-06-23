package com.playlists.app.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlayHealthTest {
    @Test
    fun tunnelStatusWhenServing_okWithoutHttpProbeWhenCloudflaredAndLocalHealthy() {
        val status = RemotePlayHealth.tunnelStatusWhenServing(
            tunnelBaseUrl = "https://abc.trycloudflare.com",
            cloudflaredRunning = true,
            localOk = true,
        )

        assertTrue(status.ok)
        assertTrue(status.detail.contains("browser"))
    }

    @Test
    fun tunnelStatusWhenServing_probesWhenCloudflaredDown() {
        val status = RemotePlayHealth.tunnelStatusWhenServing(
            tunnelBaseUrl = "https://invalid-host-that-does-not-exist.example",
            cloudflaredRunning = false,
            localOk = true,
        )

        assertFalse(status.ok)
    }
}
