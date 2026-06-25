package com.playlists.app.render

import com.playlists.app.ai.ChartDraft
import com.playlists.app.ai.ChartSection

object ChordTransposer {
    private val CHROMATIC_SHARP = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_TO_SHARP = mapOf(
        "Db" to "C#", "Eb" to "D#", "Gb" to "F#", "Ab" to "G#", "Bb" to "A#",
        "Cb" to "B", "Fb" to "E",
    )

    /** Major roots that use flats in conventional spelling (F, Bb, Eb, …). */
    private val FLAT_MAJOR_ROOTS = setOf(5, 10, 3, 8, 1, 6) // F, Bb, Eb, Ab, Db, Gb

    /** Relative-minor roots that use flats (Dm, Gm, Cm, …). */
    private val FLAT_MINOR_ROOTS = setOf(2, 7, 0, 5, 10, 3, 8) // Dm, Gm, Cm, Fm, Bbm, Ebm, Abm

    private val CHORD_PATTERN = Regex(
        """(?<![A-Za-z])([A-G])([#b])?(m(?:aj(?:7|9)?|in(?:or)?(?:7|9)?|7|6|9|11|13)?|maj7|maj9|dim(?:7)?|aug|sus[24]|add\d+|M7|M)?(\d+)?(/([A-G])([#b])?)?""",
        RegexOption.IGNORE_CASE,
    )

    fun semitonesBetween(fromKey: String, toKey: String): Int? {
        val from = rootSemitone(fromKey) ?: return null
        val to = rootSemitone(toKey) ?: return null
        return (to - from).mod(12)
    }

    fun transpose(draft: ChartDraft, targetKey: String): ChartDraft {
        val source = draft.sourceKey ?: draft.key ?: return draft.withTargetKey(targetKey)
        if (source.equals(targetKey, ignoreCase = true)) {
            return draft.withTargetKey(targetKey)
        }
        val semitones = semitonesBetween(source, targetKey) ?: return draft.withTargetKey(targetKey)
        return applySemitones(draft, semitones, targetKey)
    }

    /** Shift source chart up or down by half-steps (semitones) from its source key. */
    fun transposeBySemitones(draft: ChartDraft, semitones: Int): ChartDraft {
        if (semitones == 0) return draft
        val sourceKey = draft.sourceKey ?: draft.key ?: "C"
        val newKey = shiftKey(sourceKey, semitones)
        return applySemitones(draft, semitones, newKey)
    }

    fun shiftKey(key: String, semitones: Int): String {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return spellNote(semitones.mod(12), false)
        val match = Regex("""^([A-G])([#b]?)(.*)$""", RegexOption.IGNORE_CASE).find(trimmed)
            ?: return trimmed
        val suffix = match.groupValues[3]
        val rootSem = rootSemitone(match.groupValues[1] + match.groupValues[2]) ?: 0
        val newRootSem = (rootSem + semitones).mod(12)
        val isMinor = suffix.startsWith("m", ignoreCase = true) &&
            !suffix.startsWith("maj", ignoreCase = true)
        val newRoot = spellNote(newRootSem, prefersFlats(newRootSem, isMinor))
        return newRoot + suffix
    }

    /** True when conventional notation for this key uses flats (e.g. F → Bb not A#). */
    fun prefersFlats(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return false
        val match = Regex("""^([A-G])([#b]?)(.*)$""", RegexOption.IGNORE_CASE).find(trimmed) ?: return false
        val rootSem = rootSemitone(match.groupValues[1] + match.groupValues[2]) ?: return false
        val suffix = match.groupValues[3]
        val isMinor = suffix.startsWith("m", ignoreCase = true) &&
            !suffix.startsWith("maj", ignoreCase = true)
        return prefersFlats(rootSem, isMinor)
    }

    private fun prefersFlats(rootSemitone: Int, isMinor: Boolean): Boolean =
        if (isMinor) rootSemitone in FLAT_MINOR_ROOTS else rootSemitone in FLAT_MAJOR_ROOTS

    private fun applySemitones(draft: ChartDraft, semitones: Int, newKey: String): ChartDraft =
        draft.copy(
            key = newKey,
            sections = draft.sections.map { section ->
                section.copy(lines = section.lines.map { transposeLine(it, semitones, newKey) })
            },
        )

    fun transposeLine(line: String, semitones: Int, spellingKey: String? = null): String {
        if (semitones == 0) return line
        val preferFlat = prefersFlats(spellingKey.orEmpty())
        return CHORD_PATTERN.replace(line) { match ->
            val root = match.groupValues[1]
            val acc = match.groupValues[2]
            val quality = match.groupValues[3]
            val extension = match.groupValues[4]
            val bassRoot = match.groupValues[5]
            val bassAcc = match.groupValues[6]
            val transposedRoot = transposeNote(root, acc, semitones, preferFlat)
            val transposedBass = if (bassRoot.isNotEmpty()) {
                "/${transposeNote(bassRoot, bassAcc, semitones, preferFlat)}"
            } else {
                ""
            }
            "$transposedRoot$quality$extension$transposedBass"
        }
    }

    private fun transposeNote(
        root: String,
        accidental: String,
        semitones: Int,
        preferFlat: Boolean,
    ): String {
        val note = normalizeNote(root.uppercase() + accidental)
        val index = CHROMATIC_SHARP.indexOf(note)
        if (index < 0) return root + accidental
        val newIndex = (index + semitones).mod(12)
        return spellNote(newIndex, preferFlat)
    }

    private fun rootSemitone(key: String): Int? {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return null
        val rootMatch = Regex("""^([A-G])([#b])?""", RegexOption.IGNORE_CASE).find(trimmed) ?: return null
        val note = normalizeNote(rootMatch.groupValues[1].uppercase() + rootMatch.groupValues[2])
        return CHROMATIC_SHARP.indexOf(note).takeIf { it >= 0 }
    }

    private fun normalizeNote(note: String): String {
        if (note.length == 1) return note
        val withSharp = note.replace("b", "").let {
            when {
                note.contains("b") -> FLAT_TO_SHARP["${note[0]}b"] ?: note
                else -> note
            }
        }
        return FLAT_TO_SHARP[withSharp] ?: withSharp
    }

    private fun spellNote(index: Int, preferFlat: Boolean): String {
        val sharp = CHROMATIC_SHARP[index.mod(12)]
        if (!preferFlat || !sharp.contains("#")) return sharp
        return when (sharp) {
            "C#" -> "Db"
            "D#" -> "Eb"
            "F#" -> "Gb"
            "G#" -> "Ab"
            "A#" -> "Bb"
            else -> sharp
        }
    }
}
