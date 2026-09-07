package com.playlists.app.util

import com.playlists.app.data.FileType
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingImportTest {
    @Test
    fun pageCount_includesExtraPages() {
        val pending = PendingImport(
            filePath = "/tmp/a.jpg",
            fileType = FileType.IMAGE,
            suggestedTitle = "Song",
            extraPagePaths = listOf("/tmp/b.jpg", "/tmp/c.jpg"),
            allowAddPages = true,
        )
        assertEquals(3, pending.pageCount)
        assertEquals(3, pending.allPageFiles.size)
        assertEquals("/tmp/a.jpg", pending.allPageFiles[0].path)
        assertEquals("/tmp/c.jpg", pending.allPageFiles[2].path)
    }
}
