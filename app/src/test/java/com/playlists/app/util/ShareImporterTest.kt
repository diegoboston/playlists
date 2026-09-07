package com.playlists.app.util

import com.playlists.app.data.FileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareImporterTest {
    @Test
    fun extractUrl_findsFirstHttpUrl() {
        assertEquals(
            "https://tabs.ultimate-guitar.com/tab/oasis/wonderwall-chords-123",
            ShareImporter.extractUrl("Check this https://tabs.ultimate-guitar.com/tab/oasis/wonderwall-chords-123"),
        )
    }

    @Test
    fun extractUrl_trimsTrailingPunctuation() {
        assertEquals(
            "https://example.com/song",
            ShareImporter.extractUrl("(https://example.com/song)."),
        )
    }

    @Test
    fun extractUrl_returnsNullForPlainText() {
        assertNull(ShareImporter.extractUrl("not a link"))
    }

    @Test
    fun titleHintFromUrl_usesLastPathSegment() {
        assertEquals(
            "wonderwall-chords-123",
            ShareImporter.titleHintFromUrl("https://tabs.ultimate-guitar.com/tab/oasis/wonderwall-chords-123"),
        )
    }

    @Test
    fun fileTypeForMime_mapsImagesAndPdfs() {
        assertEquals(FileType.IMAGE, ShareImporter.fileTypeForMime("image/jpeg"))
        assertEquals(FileType.IMAGE, ShareImporter.fileTypeForMime("image/*"))
        assertEquals(FileType.PDF, ShareImporter.fileTypeForMime("application/pdf"))
        assertNull(ShareImporter.fileTypeForMime("text/plain"))
        assertNull(ShareImporter.fileTypeForMime("application/zip"))
    }

    @Test
    fun mimeFromDisplayName_usesExtension() {
        assertEquals("application/pdf", ShareImporter.mimeFromDisplayName("Chart.pdf"))
        assertEquals("image/png", ShareImporter.mimeFromDisplayName("folder/photo.PNG"))
        assertNull(ShareImporter.mimeFromDisplayName("notes.txt"))
        assertNull(ShareImporter.mimeFromDisplayName(null))
    }
}
