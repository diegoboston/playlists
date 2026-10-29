package com.playlists.app.remote

data class RemotePlayDebugInfo(
    val mode: RemotePlayMode,
    val localPort: Int,
    val localUrl: String,
    val tunnelBaseUrl: String?,
    val publicUrl: String,
    val serverAlive: Boolean,
    val tunnelProcessAlive: Boolean,
    val tunnelExitCode: Int?,
    val localProbe: RemotePlayHealth.ProbeResult,
    val tunnelProbe: RemotePlayHealth.ProbeResult?,
    val cloudflaredLog: String,
    val warnings: List<String>,
    val checkedAtMs: Long,
    val urlProbes: List<RemotePlayHealth.UrlProbe> = emptyList(),
    val lastRegisteredProtocol: String? = null,
) {
    fun connectionSummary(): String = when {
        !serverAlive || !localProbe.ok -> "Local web server is unavailable"
        !mode.usesCloudflareTunnel() -> "Local web server is responding; check LAN addresses below"
        !tunnelProcessAlive -> "Local web server is responding; tunnel process is stopped"
        tunnelProbe?.ok == true -> "Public tunnel responded to an HTTP check from this phone"
        tunnelProbe == null -> "Tunnel process is running; public reachability is unverified"
        else -> "Tunnel process is running; public HTTP check failed"
    }

    /** True when something looks wrong — used to decide whether to show checks/logs in the UI. */
    fun hasIssues(): Boolean {
        if (warnings.isNotEmpty() || urlProbes.any { !it.result.ok }) return true
        if (!serverAlive || !localProbe.ok) return true
        if (mode == RemotePlayMode.CLOUDFLARE || mode == RemotePlayMode.STABLE) {
            if (!tunnelProcessAlive) return true
            if (tunnelProbe?.ok == false) return true
        }
        return false
    }

    /** True when cloudflared or the quick tunnel is unhealthy (not stable-redirect KV issues). */
    fun hasCloudflareIssues(): Boolean {
        if (mode != RemotePlayMode.CLOUDFLARE && mode != RemotePlayMode.STABLE) return false
        if (!tunnelProcessAlive) return true
        if (tunnelProbe?.ok == false) return true
        return warnings.any { it.looksCloudflareSpecific() }
    }

    fun formatForCopy(): String = buildString {
        appendLine("Stage Manager — remote play debug")
        appendLine("Checked: ${java.text.SimpleDateFormat.getDateTimeInstance().format(java.util.Date(checkedAtMs))}")
        appendLine("Mode: $mode")
        appendLine("Public URL: $publicUrl")
        appendLine("Local URL (internal address — this phone only): $localUrl")
        tunnelBaseUrl?.let { appendLine("Tunnel base: $it") }
        appendLine("HTTP server alive: $serverAlive")
        appendLine("cloudflared running (process only): $tunnelProcessAlive")
        appendLine("Connection: ${connectionSummary()}")
        lastRegisteredProtocol?.let { appendLine("Last registered transport: $it") }
        tunnelExitCode?.let { appendLine("cloudflared exit code: $it") }
        appendLine()
        appendLine("Local probe: ${probeLine(localProbe)}")
        tunnelProbe?.let { appendLine("Tunnel probe: ${probeLine(it)}") }
        urlProbes.forEach { probe ->
            appendLine("${probe.label}: ${probe.result.label} — ${probeLine(probe.result)}")
        }
        if (warnings.isNotEmpty()) {
            appendLine()
            appendLine("Warnings:")
            warnings.forEach { appendLine("• $it") }
        }
        if (cloudflaredLog.isNotBlank()) {
            appendLine()
            appendLine("--- cloudflared log (tail) ---")
            append(cloudflaredLog)
        }
    }

    private fun probeLine(probe: RemotePlayHealth.ProbeResult): String =
        if (probe.ok) "OK — ${probe.detail}" else "FAIL — ${probe.detail}"
}

internal fun String.looksCloudflareSpecific(): Boolean {
    val lower = lowercase()
    return lower.contains("cloudflared") ||
        lower.contains("tunnel not reachable") ||
        lower.contains("tunnel url")
}
