package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OrphanSongFilesTest {
    @Test
    fun findOrphans_listsFilesNotReferencedByAnySong() {
        val root = createTempDir(prefix = "orphan-test")
        val songsDir = File(root, "songs").apply { mkdirs() }
        File(songsDir, "linked.pdf").apply { writeText("x") }
        File(songsDir, "orphan.pdf").writeText("y")
        File(songsDir, "other.pdf").writeText("z")

        val orphans = OrphanSongFiles.findOrphans(
            songsDir,
            listOf("Music/StageManager/songs/linked.pdf"),
        )

        assertEquals(listOf("orphan.pdf", "other.pdf"), orphans.map { it.name })
    }

    @Test
    fun findOrphans_treatsSdcardAndEmulatedDbPathsAsSameFile() {
        val root = createTempDir(prefix = "orphan-alias")
        val songsDir = File(root, "songs").apply { mkdirs() }
        File(songsDir, "linked.pdf").apply { writeText("x") }
        File(songsDir, "orphan.pdf").writeText("y")

        val orphans = OrphanSongFiles.findOrphans(
            songsDir,
            listOf("/sdcard/Music/StageManager/songs/linked.pdf"),
        )

        assertEquals(listOf("orphan.pdf"), orphans.map { it.name })
    }

    @Test
    fun findOrphans_matchesReferencedFilesByBasenameFromStaleAbsolutePath() {
        val root = createTempDir(prefix = "orphan-basename")
        val songsDir = File(root, "songs").apply { mkdirs() }
        File(songsDir, "linked.pdf").apply { writeText("x") }
        File(songsDir, "orphan.pdf").writeText("y")
        val staleDbPath = "/data/user/0/app/files/songs/linked.pdf"

        val orphans = OrphanSongFiles.findOrphans(songsDir, listOf(staleDbPath))

        assertEquals(listOf("orphan.pdf"), orphans.map { it.name })
    }

    @Test
    fun findOrphans_ignoresNonFilesAndEmptyDir() {
        val root = createTempDir(prefix = "orphan-empty")
        val songsDir = File(root, "songs").apply { mkdirs() }
        File(songsDir, "nested").mkdirs()

        assertTrue(OrphanSongFiles.findOrphans(songsDir, emptyList()).isEmpty())
        assertTrue(OrphanSongFiles.findOrphans(File(root, "missing"), emptyList()).isEmpty())
    }

    @Test
    fun deleteFiles_removesOnlyExistingFiles() {
        val root = createTempDir(prefix = "orphan-delete")
        val a = File(root, "a.pdf").apply { writeText("a") }
        val b = File(root, "b.pdf").apply { writeText("b") }

        val deleted = OrphanSongFiles.deleteFiles(listOf(a, b, File(root, "missing.pdf")))

        assertEquals(2, deleted)
        assertTrue(!a.exists() && !b.exists())
    }
}
