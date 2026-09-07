package com.playlists.app.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePagesPdfTest {
    @Test
    fun shouldCombine_onlyWhenMultiplePages() {
        assertFalse(ImagePagesPdf.shouldCombine(1))
        assertFalse(ImagePagesPdf.shouldCombine(0))
        assertTrue(ImagePagesPdf.shouldCombine(2))
        assertTrue(ImagePagesPdf.shouldCombine(5))
    }

    @Test
    fun fit_centersTallPageAroundWideImage() {
        val dest = ImagePagesPdf.fit(100, 50, 200, 200)
        assertEquals(0, dest.left)
        assertEquals(200, dest.right)
        assertEquals(50, dest.top)
        assertEquals(150, dest.bottom)
    }

    @Test
    fun fit_letterboxesWideImage() {
        val dest = ImagePagesPdf.fit(400, 100, 200, 200)
        assertEquals(0, dest.left)
        assertEquals(200, dest.right)
        assertEquals(75, dest.top)
        assertEquals(125, dest.bottom)
    }
}
