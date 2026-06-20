package com.playlists.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderLogicTest {
    @Test
    fun swapDownWhenCenterCrossesNeighbor() {
        val keys = mutableListOf("a", "b", "c")
        val tops = mapOf("a" to 0f, "b" to 100f, "c" to 200f)
        val heights = mapOf("a" to 80f, "b" to 80f, "c" to 80f)
        val swapped = ReorderLogic.handleDrag("a", dragVisualTop = 110f, keys, tops, heights)
        assertEquals(true, swapped)
        assertEquals(listOf("b", "a", "c"), keys)
    }

    @Test
    fun noSwapWithoutCrossingCenter() {
        val keys = mutableListOf("a", "b")
        val tops = mapOf("a" to 0f, "b" to 100f)
        val heights = mapOf("a" to 80f, "b" to 80f)
        val swapped = ReorderLogic.handleDrag("a", dragVisualTop = 10f, keys, tops, heights)
        assertEquals(false, swapped)
        assertEquals(listOf("a", "b"), keys)
    }
}
