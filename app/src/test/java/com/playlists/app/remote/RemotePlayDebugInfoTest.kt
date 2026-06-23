package com.playlists.app.remote

import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlayDebugInfoTest {
    @Test
    fun formatForCopy_includesProbesAndWarnings() {
        val info = RemotePlayDebugInfo(
            mode = RemotePlayMode.CLOUDFLARE,
            localPort = 52341,
            localUrl = "http://127.0.0.1:52341/",
            tunnelBaseUrl = "https://abc.trycloudflare.com",
            publicUrl = "https://abc.trycloudflare.com/",
            serverAlive = true,
            tunnelProcessAlive = false,
            tunnelExitCode = 1,
            localProbe = RemotePlayHealth.ProbeResult("http://127.0.0.1:52341/", true, "HTTP 200"),
            tunnelProbe = RemotePlayHealth.ProbeResult(
                "https://abc.trycloudflare.com/",
                false,
                "failed to connect (after 3 attempts)",
            ),
            cloudflaredLog = "cloudflared exited",
            warnings = listOf("cloudflared is not running — the public URL will not work."),
            checkedAtMs = 1_700_000_000_000L,
        )
        val text = info.formatForCopy()
        assertTrue(text.contains("Local probe: OK"))
        assertTrue(text.contains("Tunnel probe: FAIL"))
        assertTrue(text.contains("cloudflared is not running"))
        assertTrue(text.contains("cloudflared exited"))
    }
}
