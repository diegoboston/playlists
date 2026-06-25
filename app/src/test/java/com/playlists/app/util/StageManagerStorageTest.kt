package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StageManagerStorageTest {
    @Test
    fun directorySizeBytes_sumsNestedFiles() {
        val root = File.createTempFile("stage", null)
        root.delete()
        root.mkdirs()
        val songs = File(root, "songs").also { it.mkdirs() }
        File(songs, "a.pdf").writeBytes(ByteArray(100))
        File(songs, "a.chart.json").writeBytes(ByteArray(50))
        File(root, "playlists.db").writeBytes(ByteArray(25))
        assertEquals(175L, StageManagerStorage.directorySizeBytes(root))
    }
}
