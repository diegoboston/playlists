package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class StorageMigrationTest {
    @Test
    fun remapPath_rewritesInternalSongRoot() {
        val internal = "/data/user/0/com.playlists.app/files/songs"
        val external = "/storage/emulated/0/Music/StageManager/songs"
        val old = "$internal/abc.pdf"
        assertEquals("$external/abc.pdf", StorageMigration.remapPath(old, internal, external))
    }

    @Test
    fun remapPath_leavesExternalPathsUntouched() {
        val external = "/storage/emulated/0/Music/StageManager/songs"
        val old = "$external/abc.pdf"
        assertNull(StorageMigration.remapPath(old, "/internal/songs", external))
    }

    @Test
    fun remapPath_matchesByFilenameWhenFileExists() {
        val tempRoot = createTempDir(prefix = "stage-manager-test")
        val external = File(tempRoot, "songs").apply { mkdirs() }
        val target = File(external, "sheet.pdf")
        target.writeText("test")
        val old = "/some/old/path/sheet.pdf"
        assertEquals(target.absolutePath, StorageMigration.remapPath(old, "/internal", external.absolutePath))
    }
}
