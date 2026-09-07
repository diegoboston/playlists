package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileStorageTest {
    @Test
    fun extensionForMime_mapsCommonTypes() {
        assertEquals("pdf", FileStorage.extensionForMime("application/pdf"))
        assertEquals("png", FileStorage.extensionForMime("image/png"))
        assertEquals("jpg", FileStorage.extensionForMime("image/jpeg"))
        assertEquals("jpg", FileStorage.extensionForMime("image/*"))
        assertEquals("bin", FileStorage.extensionForMime(null))
        assertEquals("bin", FileStorage.extensionForMime("*/*"))
    }
}
