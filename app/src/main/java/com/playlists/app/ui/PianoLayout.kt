package com.playlists.app.ui

import kotlin.math.pow

/** Layout math for a horizontally scrollable piano keyboard. */
object PianoLayout {
    const val MIN_MIDI = 36 // C2
    const val MAX_MIDI = 96 // C7
    /** Roughly one and a half octaves (7 white keys per octave). */
    const val VISIBLE_WHITE_KEYS = 10.5f

    private val BLACK_SEMITONES = setOf(1, 3, 6, 8, 10)

    fun isBlackKey(midi: Int): Boolean = (midi % 12) in BLACK_SEMITONES

    fun isWhiteKey(midi: Int): Boolean = !isBlackKey(midi)

    fun midiToHz(midi: Int): Float =
        (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()

    /** Index of [midi]'s white key counting from [MIN_MIDI]. */
    fun whiteKeyIndex(midi: Int): Int {
        var index = 0
        for (note in MIN_MIDI until midi) {
            if (isWhiteKey(note)) index++
        }
        return index
    }

    fun whiteKeyCount(): Int = whiteKeyIndex(MAX_MIDI + 1)

    fun whiteKeyX(midi: Int, whiteKeyWidthPx: Float): Float =
        whiteKeyIndex(midi) * whiteKeyWidthPx

    fun blackKeyX(midi: Int, whiteKeyWidthPx: Float): Float {
        var prev = midi - 1
        while (prev >= MIN_MIDI && isBlackKey(prev)) prev--
        return whiteKeyX(prev, whiteKeyWidthPx) + whiteKeyWidthPx * 0.68f
    }

    fun totalWidthPx(whiteKeyWidthPx: Float): Float =
        whiteKeyCount() * whiteKeyWidthPx

    fun maxScrollPx(whiteKeyWidthPx: Float, viewportWidthPx: Float): Float =
        (totalWidthPx(whiteKeyWidthPx) - viewportWidthPx).coerceAtLeast(0f)

    fun initialScrollPx(whiteKeyWidthPx: Float, viewportWidthPx: Float): Float {
        val c4Center = whiteKeyX(/* C4 = */ 60, whiteKeyWidthPx) + whiteKeyWidthPx / 2f
        return (c4Center - viewportWidthPx / 2f).coerceIn(0f, maxScrollPx(whiteKeyWidthPx, viewportWidthPx))
    }

    /** Scientific pitch name for C naturals (e.g. C2, C3); null for other notes. */
    fun cLabel(midi: Int): String? {
        if (midi % 12 != 0) return null
        return "C${midi / 12 - 1}"
    }

    /**
     * Hit-test in keyboard content coordinates (viewport x + [scrollOffsetPx]).
     */
    fun midiAt(
        contentX: Float,
        y: Float,
        whiteKeyWidthPx: Float,
        keyboardHeightPx: Float,
        blackKeyWidthPx: Float,
    ): Int? {
        val blackHeight = keyboardHeightPx * 0.62f
        if (y <= blackHeight) {
            for (midi in MAX_MIDI downTo MIN_MIDI) {
                if (!isBlackKey(midi)) continue
                val x = blackKeyX(midi, whiteKeyWidthPx)
                if (contentX >= x && contentX < x + blackKeyWidthPx) return midi
            }
        }
        for (midi in MIN_MIDI..MAX_MIDI) {
            if (!isWhiteKey(midi)) continue
            val x = whiteKeyX(midi, whiteKeyWidthPx)
            if (contentX >= x && contentX < x + whiteKeyWidthPx) return midi
        }
        return null
    }
}
