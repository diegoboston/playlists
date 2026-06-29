package com.playlists.app.util

import com.playlists.app.ui.SongDisplay

/**
 * Canonical song media names: `{sanitizedTitle}-{songId}.{ext}`.
 * Chart sidecars use the same base: `{sanitizedTitle}-{songId}.chart.json`.
 */
object SongFileNames {
    private val UUID_FILE = Regex(
        """^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.[^.]+$""",
        RegexOption.IGNORE_CASE,
    )

    fun sanitizeTitle(title: String): String {
        val stripped = title.replace(SongDisplay.PLACEHOLDER_MARKER, "").trim()
        val sanitized = stripped
            .replace(Regex("""[^\w\s.-]"""), "_")
            .replace(Regex("""\s+"""), "_")
            .replace(Regex("""_+"""), "_")
            .trim('_', '.')
        return sanitized.ifBlank { "Untitled" }
    }

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "pdf").lowercase()

    fun mediaFileName(title: String, songId: Long, extension: String): String {
        val ext = extension.removePrefix(".").lowercase()
        return "${sanitizeTitle(title)}-$songId.$ext"
    }

    fun chartFileName(title: String, songId: Long): String =
        "${sanitizeTitle(title)}-$songId.${ChartDraftStore.CHART_EXTENSION}"

    fun isUuidFileName(fileName: String): Boolean = UUID_FILE.matches(fileName)

    fun isCanonicalMediaFileName(fileName: String): Boolean {
        if (isUuidFileName(fileName)) return false
        if (fileName.startsWith("placeholder-", ignoreCase = true)) return false
        return fileName.matches(
            Regex("""^.+-\d+\.(pdf|png|jpe?g|webp|gif)$""", RegexOption.IGNORE_CASE),
        )
    }

    fun matchesSong(fileName: String, title: String, songId: Long): Boolean {
        val ext = extensionOf(fileName)
        return fileName == mediaFileName(title, songId, ext)
    }
}
