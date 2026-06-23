package com.playlists.app.util

import java.io.File

/**
 * Canonical on-disk song filenames: `{Title_With_Underscores}-{Key}-{songId}.{ext}`.
 * Example: `Amazing_Grace-G-42.pdf`
 */
object SongFileNaming {
    private val INVALID_CHARS = Regex("""[\\/:*?"<>|]""")
    private val WHITESPACE = Regex("""\s+""")

    fun format(title: String, keySignature: String, songId: Long, extension: String): String {
        val titlePart = sanitizeTitle(title)
        val keyPart = sanitizeKey(keySignature)
        val ext = extension.trim().trimStart('.').ifBlank { "bin" }
        return "$titlePart-$keyPart-$songId.$ext"
    }

    fun matches(title: String, keySignature: String, songId: Long, file: File): Boolean {
        val ext = file.name.substringAfterLast('.', "").ifBlank { "bin" }
        return file.name == format(title, keySignature, songId, ext)
    }

    fun sanitizeTitle(title: String): String {
        val cleaned = INVALID_CHARS.replace(title.trim(), "")
            .replace(WHITESPACE, "_")
            .trim('_')
        return cleaned.ifBlank { "Untitled" }
    }

    fun sanitizeKey(keySignature: String): String =
        INVALID_CHARS.replace(keySignature.trim(), "")
            .replace(WHITESPACE, "")

    fun uniqueDestFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base-$index$ext")
            index++
        }
        return candidate
    }

    fun resolveTargetFile(source: File, title: String, keySignature: String, songId: Long): File {
        val ext = source.extension.ifBlank { "bin" }
        val dir = source.parentFile ?: error("Song file has no parent directory: ${source.absolutePath}")
        return uniqueDestFile(dir, format(title, keySignature, songId, ext))
    }
}
