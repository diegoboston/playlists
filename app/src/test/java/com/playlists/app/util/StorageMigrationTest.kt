package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class StorageMigrationTest {
    @Test
    fun remapPath_rewritesInternalSongRootToRelativeStoredPath() {
        val internal = "/data/user/0/com.playlists.app/files/songs"
        val external = "/storage/emulated/0/Music/StageManager/songs"
        val old = "$internal/abc.pdf"
        assertEquals("Music/StageManager/songs/abc.pdf", StorageMigration.remapPath(old, internal, external))
    }

    @Test
    fun remapPath_leavesRelativeStoredPathsUntouched() {
        val external = "/storage/emulated/0/Music/StageManager/songs"
        val old = "Music/StageManager/songs/abc.pdf"
        assertNull(StorageMigration.remapPath(old, "/internal/songs", external))
    }

    @Test
    fun remapPath_matchesByFilenameWhenFileExists() {
        val tempRoot = createTempDir(prefix = "stage-manager-test")
        val external = File(tempRoot, "songs").apply { mkdirs() }
        val target = File(external, "sheet.pdf")
        target.writeText("test")
        val old = "/some/old/path/sheet.pdf"
        assertEquals("Music/StageManager/songs/sheet.pdf", StorageMigration.remapPath(old, "/internal", external.absolutePath))
    }

    @Test
    fun remapPath_convertsExternalAbsolutePathToRelative() {
        val external = "/storage/emulated/0/Music/StageManager/songs"
        val old = "$external/abc.pdf"
        assertEquals("Music/StageManager/songs/abc.pdf", StorageMigration.remapPath(old, "/internal/songs", external))
    }
}
