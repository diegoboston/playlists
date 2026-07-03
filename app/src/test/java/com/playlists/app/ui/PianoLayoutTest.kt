package com.playlists.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PianoLayoutTest {
    @Test
    fun whiteKeyIndex_countsFromMinMidi() {
        assertEquals(0, PianoLayout.whiteKeyIndex(36)) // C2
        assertEquals(7, PianoLayout.whiteKeyIndex(48)) // C3
        assertEquals(14, PianoLayout.whiteKeyIndex(60)) // C4
    }

    @Test
    fun isBlackKey_identifiesSharps() {
        assertFalse(PianoLayout.isBlackKey(60)) // C4
        assertTrue(PianoLayout.isBlackKey(61)) // C#4
        assertTrue(PianoLayout.isBlackKey(63)) // D#4
    }

    @Test
    fun midiAt_prefersBlackKeyInUpperRegion() {
        val whiteKeyWidth = 38f
        val blackKeyWidth = whiteKeyWidth * 0.55f
        val height = 120f
        val cs4X = PianoLayout.blackKeyX(61, whiteKeyWidth) + blackKeyWidth / 2f
        assertEquals(
            61,
            PianoLayout.midiAt(
                contentX = cs4X,
                y = 10f,
                whiteKeyWidthPx = whiteKeyWidth,
                keyboardHeightPx = height,
                blackKeyWidthPx = blackKeyWidth,
            ),
        )
    }

    @Test
    fun midiAt_returnsWhiteKeyBelowBlackRegion() {
        val whiteKeyWidth = 38f
        val blackKeyWidth = whiteKeyWidth * 0.55f
        val height = 120f
        val d4X = PianoLayout.whiteKeyX(62, whiteKeyWidth) + whiteKeyWidth / 2f
        assertEquals(
            62,
            PianoLayout.midiAt(
                contentX = d4X,
                y = height * 0.8f,
                whiteKeyWidthPx = whiteKeyWidth,
                keyboardHeightPx = height,
                blackKeyWidthPx = blackKeyWidth,
            ),
        )
    }

    @Test
    fun midiAt_returnsNullOutsideKeys() {
        assertNull(
            PianoLayout.midiAt(
                contentX = -10f,
                y = 50f,
                whiteKeyWidthPx = 38f,
                keyboardHeightPx = 100f,
                blackKeyWidthPx = 20f,
            ),
        )
    }

    @Test
    fun cLabel_marksCNaturalOctaves() {
        assertEquals("C2", PianoLayout.cLabel(36))
        assertEquals("C3", PianoLayout.cLabel(48))
        assertEquals("C4", PianoLayout.cLabel(60))
        assertNull(PianoLayout.cLabel(61))
    }

    @Test
    fun midiToHz_isConcertA() {
        assertEquals(440f, PianoLayout.midiToHz(69), 0.01f)
    }
}
