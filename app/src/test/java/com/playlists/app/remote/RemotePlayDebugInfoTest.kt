package com.playlists.app.remote

import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlayDebugInfoTest {
    private fun sampleInfo(
        mode: RemotePlayMode = RemotePlayMode.CLOUDFLARE,
        warnings: List<String> = emptyList(),
        serverAlive: Boolean = true,
        tunnelProcessAlive: Boolean = true,
        localOk: Boolean = true,
        tunnelOk: Boolean = true,
    ) = RemotePlayDebugInfo(
        mode = mode,
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
    fun failedAdvertisedUrlIsAnIssueAndIsIncludedInDebugCopy() {
        val info = sampleInfo().copy(urlProbes = listOf(
            RemotePlayHealth.UrlProbe("Stable URL", RemotePlayHealth.ProbeResult(
                "https://play.example.com/", false, "HTTP 530",
            )),
        ))
        assertTrue(info.hasIssues())
        assertTrue(info.formatForCopy().contains("Stable URL: https://play.example.com/ — FAIL — HTTP 530"))
    }

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
    fun hasCloudflareIssues_falseWhenOnlyStableUrlWarnings() {
        val info = sampleInfo(
            mode = RemotePlayMode.STABLE,
            warnings = listOf("Could not register stable URL (HTTP 404)."),
        )
        assertTrue(!info.hasCloudflareIssues())
    }

    @Test
    fun hasCloudflareIssues_trueWhenTunnelProbeFails() {
        assertTrue(sampleInfo(tunnelOk = false).hasCloudflareIssues())
    }

    @Test
    fun hasCloudflareIssues_trueWhenCloudflaredWarning() {
        assertTrue(
            sampleInfo(warnings = listOf("cloudflared is not running — the public URL will not work."))
                .hasCloudflareIssues(),
        )
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
    @Test
    fun statusDoesNotConfuseRunningProcessWithPublicReachability() {
        assertTrue(sampleInfo(tunnelOk = false).connectionSummary().contains("public HTTP check failed"))
        assertTrue(sampleInfo().copy(tunnelProbe = null).connectionSummary().contains("unverified"))
        assertTrue(sampleInfo(tunnelProcessAlive = false).connectionSummary().contains("process is stopped"))
        assertTrue(sampleInfo(localOk = false).connectionSummary().contains("Local web server is unavailable"))
    }

    @Test
    fun workingStableUrlDoesNotHideBrokenDirectTunnel() {
        val info = sampleInfo(mode = RemotePlayMode.STABLE, tunnelOk = false).copy(
            urlProbes = listOf(RemotePlayHealth.UrlProbe("Stable URL",
                RemotePlayHealth.ProbeResult("https://play.example.com", true, "HTTP 200"))),
            lastRegisteredProtocol = "http2",
        )
        assertTrue(info.connectionSummary().contains("public HTTP check failed"))
        assertTrue(info.hasCloudflareIssues())
        assertTrue(info.formatForCopy().contains("Last registered transport: http2"))
        assertTrue(info.formatForCopy().contains("this phone only"))
    }
}
