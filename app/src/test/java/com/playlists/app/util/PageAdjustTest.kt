package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageGeometryTest {
    @Test
    fun contentFit_letterboxesInsideView() {
        val fit = PageGeometry.contentFit(100, 50, 200, 200)
        assertEquals(0f, fit.left, 0.01f)
        assertEquals(50f, fit.top, 0.01f)
        assertEquals(200f, fit.width, 0.01f)
        assertEquals(100f, fit.height, 0.01f)
    }

    @Test
    fun destSize_usesAverageSideLengths() {
        val quad = PageQuad(
            PagePoint(0f, 0f),
            PagePoint(100f, 0f),
            PagePoint(100f, 50f),
            PagePoint(0f, 50f),
        )
        val (width, height) = PageGeometry.destSize(quad)
        assertEquals(100, width)
        assertEquals(50, height)
    }

    @Test
    fun destSize_capsLongestEdge() {
        val quad = PageQuad(
            PagePoint(0f, 0f),
            PagePoint(6000f, 0f),
            PagePoint(6000f, 3000f),
            PagePoint(0f, 3000f),
        )
        val (width, height) = PageGeometry.destSize(quad, maxEdge = 3000)
        assertEquals(3000, width)
        assertEquals(1500, height)
    }

    @Test
    fun isUsableNormalized_acceptsInsetPage() {
        assertTrue(PageGeometry.isUsableNormalized(PageQuad.normalizedInset()))
    }

    @Test
    fun isUsableNormalized_rejectsBowtie() {
        val bowtie = PageQuad(
            PagePoint(0.2f, 0.2f),
            PagePoint(0.8f, 0.2f),
            PagePoint(0.2f, 0.8f),
            PagePoint(0.8f, 0.8f),
        )
        assertFalse(PageGeometry.isUsableNormalized(bowtie))
    }

    @Test
    fun viewMapping_roundTrips() {
        val fit = FitRect(10f, 20f, 100f, 200f)
        val x = PageGeometry.fromViewX(60f, fit)
        val y = PageGeometry.fromViewY(120f, fit)
        assertEquals(0.5f, x, 0.001f)
        assertEquals(0.5f, y, 0.001f)
        assertEquals(60f, PageGeometry.toViewX(x, fit), 0.001f)
        assertEquals(120f, PageGeometry.toViewY(y, fit), 0.001f)
    }
}

class PageDetectorTest {
    @Test
    fun detectNormalized_findsBrightRectangle() {
        val width = 100
        val height = 100
        val luma = ByteArray(width * height)
        for (y in 15 until 85) {
            for (x in 20 until 80) {
                luma[y * width + x] = 0xFF.toByte()
            }
        }
        val quad = PageDetector.detectNormalized(luma, width, height)
        assertNotNull(quad)
        val found = quad!!
        assertEquals(0.20f, found.topLeft.x, 0.05f)
        assertEquals(0.15f, found.topLeft.y, 0.05f)
        assertEquals(0.79f, found.topRight.x, 0.05f)
        assertEquals(0.15f, found.topRight.y, 0.05f)
        assertEquals(0.79f, found.bottomRight.x, 0.05f)
        assertEquals(0.84f, found.bottomRight.y, 0.05f)
        assertEquals(0.20f, found.bottomLeft.x, 0.05f)
        assertEquals(0.84f, found.bottomLeft.y, 0.05f)
    }

    @Test
    fun detectNormalized_skipsFullFramePaper() {
        val luma = ByteArray(64 * 64) { 0xFF.toByte() }
        assertNull(PageDetector.detectNormalized(luma, 64, 64))
    }

    @Test
    fun detectNormalized_skipsTinyBlob() {
        val width = 80
        val height = 80
        val luma = ByteArray(width * height)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                luma[y * width + x] = 0xFF.toByte()
            }
        }
        assertNull(PageDetector.detectNormalized(luma, width, height))
    }
}
