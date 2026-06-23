package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SongStoragePathsTest {
    @Test
    fun normalizeStoredPath_collapsesSdcardAndEmulatedPathsToSameRelativeForm() {
        val name = "linked.pdf"
        val sdcard = "/sdcard/Music/StageManager/songs/$name"
        val emulated = "/storage/emulated/0/Music/StageManager/songs/$name"
        val expected = "Music/StageManager/songs/$name"

        assertEquals(expected, SongStoragePaths.normalizeStoredPath(sdcard))
        assertEquals(expected, SongStoragePaths.normalizeStoredPath(emulated))
        assertEquals(expected, SongStoragePaths.normalizeStoredPath(expected))
    }

    @Test
    fun fileName_extractsFromRelativeOrAbsolutePaths() {
        val relative = "Music/StageManager/songs/Amazing_Grace-G-42.pdf"
        val sdcard = "/sdcard/Music/StageManager/songs/Amazing_Grace-G-42.pdf"

        assertEquals("Amazing_Grace-G-42.pdf", SongStoragePaths.fileName(relative))
        assertEquals("Amazing_Grace-G-42.pdf", SongStoragePaths.fileName(sdcard))
    }

    @Test
    fun isRelativeSongPath_detectsStoredForm() {
        assertTrue(SongStoragePaths.isRelativeSongPath("Music/StageManager/songs/foo.pdf"))
        assertFalse(SongStoragePaths.isRelativeSongPath("/storage/emulated/0/Music/StageManager/songs/foo.pdf"))
    }

    @Test
    fun toStoredPath_usesFileNameOnly() {
        val file = File("/any/prefix/songs/Test_Song-G-1.pdf")
        assertEquals("Music/StageManager/songs/Test_Song-G-1.pdf", SongStoragePaths.toStoredPath(file))
    }
}
