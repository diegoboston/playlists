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
        val linked = File(songsDir, "linked.pdf").apply { writeText("x") }
        File(songsDir, "orphan.pdf").writeText("y")
        File(songsDir, "other.pdf").writeText("z")

        val orphans = OrphanSongFiles.findOrphans(songsDir, listOf(linked.absolutePath))

        assertEquals(listOf("orphan.pdf", "other.pdf"), orphans.map { it.name })
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
