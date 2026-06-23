package com.playlists.app.remote

import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlayDebugInfoTest {
    private fun sampleInfo(
        warnings: List<String> = emptyList(),
        serverAlive: Boolean = true,
        tunnelProcessAlive: Boolean = true,
        localOk: Boolean = true,
        tunnelOk: Boolean = true,
    ) = RemotePlayDebugInfo(
        mode = RemotePlayMode.CLOUDFLARE,
        localPort = 52341,
        localUrl = "http://127.0.0.1:52341/",
        tunnelBaseUrl = "https://abc.trycloudflare.com",
        publicUrl = "https://abc.trycloudflare.com/",
        serverAlive = serverAlive,
        tunnelProcessAlive = tunnelProcessAlive,
        tunnelExitCode = null,
        localProbe = RemotePlayHealth.ProbeResult("http://127.0.0.1:52341/", localOk, "HTTP 200"),
        tunnelProbe = RemotePlayHealth.ProbeResult("https://abc.trycloudflare.com/", tunnelOk, "OK"),
        cloudflaredLog = "tunnel ready",
        warnings = warnings,
        checkedAtMs = 1_700_000_000_000L,
    )

    @Test
    fun hasIssues_falseWhenHealthy() {
        assertTrue(!sampleInfo().hasIssues())
    }

    @Test
    fun hasIssues_trueWhenWarningsOrProbesFail() {
        assertTrue(sampleInfo(warnings = listOf("cloudflared is not running")).hasIssues())
        assertTrue(sampleInfo(serverAlive = false).hasIssues())
        assertTrue(sampleInfo(localOk = false).hasIssues())
        assertTrue(sampleInfo(tunnelProcessAlive = false).hasIssues())
        assertTrue(sampleInfo(tunnelOk = false).hasIssues())
    }

    @Test
    fun formatForCopy_includesProbesAndWarnings() {
        val info = sampleInfo(
            tunnelProcessAlive = false,
            tunnelOk = false,
            warnings = listOf("cloudflared is not running — the public URL will not work."),
        ).copy(
            tunnelExitCode = 1,
            cloudflaredLog = "cloudflared exited",
            tunnelProbe = RemotePlayHealth.ProbeResult(
                "https://abc.trycloudflare.com/",
                false,
                "failed to connect (after 3 attempts)",
            ),
        )
        val text = info.formatForCopy()
        assertTrue(text.contains("Local probe: OK"))
        assertTrue(text.contains("Tunnel probe: FAIL"))
        assertTrue(text.contains("cloudflared is not running"))
        assertTrue(text.contains("cloudflared exited"))
    }
}
