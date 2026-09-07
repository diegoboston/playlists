package com.playlists.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CaptureImageStoreTest {
    @Test
    fun isUsableCapture_requiresNonEmptyFile() {
        val missing = File("/tmp/playlists-capture-missing-${System.nanoTime()}.jpg")
        assertFalse(CaptureImageStore.isUsableCapture(missing))

        val empty = File.createTempFile("playlists-capture-empty", ".jpg")
        empty.writeBytes(ByteArray(0))
        try {
            assertFalse(CaptureImageStore.isUsableCapture(empty))
            empty.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(CaptureImageStore.isUsableCapture(empty))
        } finally {
            empty.delete()
        }
    }
}
