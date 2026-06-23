package com.playlists.app.util

/**
 * Parses song titles imported from filenames (underscores, dashes, extensions, trailing key/instrument).
 * Used by the v5→v6 DB migration, share/import, and [SongTitles.fromFilename].
 */
object SongTitleMigration {
    data class Result(
        val title: String,
        val keySignature: String,
        val notes: String,
    )

    private val FILE_EXTENSIONS = Regex("""\.(pdf|png|jpe?g|webp|gif)$""", RegexOption.IGNORE_CASE)

    /** Dashes often separate title, key, and instrument (e.g. "Song - G - piano"). */
    private val DASH_SEPARATORS = Regex("""\s*[-–—]\s*""")

    /** Longest suffix first — "electric bass" before "bass". */
    private val INSTRUMENTS = listOf(
        "electric bass",
        "electric guitar",
        "acoustic guitar",
        "accordion",
        "piano",
        "organ",
        "drums",
        "percussion",
        "violin",
        "cello",
        "flute",
        "trumpet",
        "saxophone",
        "guitar",
        "voices",
        "voice",
        "vocals",
        "vocal",
    )

    private val KEY_QUALIFIERS = setOf("major", "maj", "minor", "min", "m", "sharp", "flat")

    private val KEY_TOKEN = Regex(
        """^[A-G](?:[#b]|(?:flat|sharp))?(?:m(?:in(?:or)?)?|maj(?:or)?|dim|sus|add|M)?\d*$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(title: String, existingKey: String = "", existingNotes: String = ""): Result {
        var working = title.replace('_', ' ').trim()
        working = DASH_SEPARATORS.replace(working, " ").trim()
        working = FILE_EXTENSIONS.replace(working, "").trim()

        val tokens = working.split(Regex("""\s+""")).filter { it.isNotBlank() }.toMutableList()
        val extractedNotes = mutableListOf<String>()
        var extractedKey: String? = null

        while (tokens.isNotEmpty()) {
            val match = matchInstrumentSuffix(tokens) ?: break
            extractedNotes.add(0, match)
            repeat(match.split(' ').size) { tokens.removeAt(tokens.lastIndex) }
        }

        extractedKey = extractTrailingKey(tokens)

        val newTitle = tokens.joinToString(" ").trim().ifBlank { working }
        val newKey = extractedKey?.takeIf { it.isNotEmpty() } ?: existingKey.trim()
        val instrumentNotes = extractedNotes.joinToString(", ")
        val newNotes = mergeNotes(existingNotes, instrumentNotes)

        return Result(newTitle, newKey, newNotes)
    }

    private fun matchInstrumentSuffix(tokens: List<String>): String? {
        if (tokens.isEmpty()) return null
        val lower = tokens.map { it.lowercase() }
        for (instrument in INSTRUMENTS) {
            val parts = instrument.split(' ')
            if (lower.size >= parts.size &&
                lower.takeLast(parts.size) == parts
            ) {
                return instrument
            }
        }
        return null
    }

    private fun extractTrailingKey(tokens: MutableList<String>): String? {
        if (tokens.size < 2) return null

        if (tokens.size >= 2) {
            val qualifier = tokens.last().lowercase()
            val letter = tokens[tokens.size - 2]
            if (letter.length == 1 && letter[0] in 'A'..'G' && qualifier in KEY_QUALIFIERS) {
                val qualToken = tokens.removeAt(tokens.lastIndex)
                val keyLetter = tokens.removeAt(tokens.lastIndex)
                return normalizeKey("$keyLetter $qualToken")
            }
        }

        val last = tokens.last()
        if (isKeyToken(last)) {
            tokens.removeAt(tokens.lastIndex)
            return normalizeKey(last)
        }
        return null
    }

    internal fun isKeyToken(token: String): Boolean {
        if (KEY_TOKEN.matches(token)) return true
        return token.length == 1 && token[0].uppercaseChar() in 'A'..'G'
    }

    private fun normalizeKey(key: String): String {
        val parts = key.trim().split(Regex("""\s+"""))
        if (parts.size == 2 && parts[1].lowercase() in KEY_QUALIFIERS) {
            val letter = parts[0].uppercase()
            return when (parts[1].lowercase()) {
                "sharp" -> "$letter#"
                "flat" -> "${letter}b"
                "major", "maj" -> letter
                "minor", "min", "m" -> "${letter}m"
                else -> "$letter ${parts[1]}"
            }
        }
        return key.trim()
    }

    private fun mergeNotes(existing: String, extracted: String): String {
        val parts = listOf(existing.trim(), extracted.trim()).filter { it.isNotEmpty() }
        return parts.joinToString(", ")
    }
}
