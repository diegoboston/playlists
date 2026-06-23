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
) {
    /** True when something looks wrong — used to decide whether to show checks/logs in the UI. */
    fun hasIssues(): Boolean {
        if (warnings.isNotEmpty()) return true
        if (!serverAlive || !localProbe.ok) return true
        if (mode == RemotePlayMode.CLOUDFLARE) {
            if (!tunnelProcessAlive) return true
            if (tunnelProbe?.ok == false) return true
        }
        return false
    }

    fun formatForCopy(): String = buildString {
        appendLine("Stage Manager — remote play debug")
        appendLine("Checked: ${java.text.SimpleDateFormat.getDateTimeInstance().format(java.util.Date(checkedAtMs))}")
        appendLine("Mode: $mode")
        appendLine("Public URL: $publicUrl")
        appendLine("Local URL: $localUrl")
        tunnelBaseUrl?.let { appendLine("Tunnel base: $it") }
        appendLine("HTTP server alive: $serverAlive")
        appendLine("cloudflared running: $tunnelProcessAlive")
        tunnelExitCode?.let { appendLine("cloudflared exit code: $it") }
        appendLine()
        appendLine("Local probe: ${probeLine(localProbe)}")
        tunnelProbe?.let { appendLine("Tunnel probe: ${probeLine(it)}") }
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
