package com.playlists.app.util

import com.playlists.app.data.FileType
import com.playlists.app.data.Song
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SongFileIntegrityTest {
    private lateinit var songsDir: File

    @Before
    fun setUp() {
        songsDir = File.createTempFile("song-integrity-", "").apply {
            delete()
            mkdirs()
        }
        StageManagerStorage.setSongsDirForTests(songsDir)
    }

    @After
    fun tearDown() {
        StageManagerStorage.setSongsDirForTests(null)
        songsDir.deleteRecursively()
    }

    @Test
    fun scan_okWhenEveryRowHasItsOwnFile() {
        val songs = listOf(
            song(id = 1, title = "Amazing Grace", path = writeSongFile("Amazing_Grace-1.pdf", byteArrayOf(1))),
            song(id = 2, title = "How Great", path = writeSongFile("How_Great-2.pdf", byteArrayOf(2))),
        )

        val result = SongFileIntegrity.scan(songs)

        assertFalse(result.hasIssues)
        assertTrue(result.missingSongs.isEmpty())
        assertTrue(result.sharedFilePaths.isEmpty())
    }

    @Test
    fun scan_reportsMissingFile() {
        val songs = listOf(
            song(id = 1, title = "Missing", path = SongStoragePaths.toStoredPath(File(songsDir, "gone.pdf"))),
        )

        val result = SongFileIntegrity.scan(songs)

        assertTrue(result.hasIssues)
        assertEquals(1, result.missingSongs.size)
        assertEquals(1L, result.missingSongs.single().songId)
        assertTrue(result.sharedFilePaths.isEmpty())
    }

    @Test
    fun scan_reportsSharedStoredPath() {
        val stored = writeSongFile("shared.pdf", byteArrayOf(9))
        val songs = listOf(
            song(id = 1, title = "Song A", path = stored),
            song(id = 2, title = "Song B", path = stored),
        )

        val result = SongFileIntegrity.scan(songs)

        assertTrue(result.hasIssues)
        assertTrue(result.missingSongs.isEmpty())
        assertEquals(1, result.sharedFilePaths.size)
        val shared = result.sharedFilePaths.single()
        assertEquals(stored, shared.filePath)
        assertTrue(shared.fileExists)
        assertEquals(listOf(1L, 2L), shared.songIds)
    }

    @Test
    fun scan_sharedMissingPathAlsoListsMissingRows() {
        val stored = SongStoragePaths.toStoredPath(File(songsDir, "missing-shared.pdf"))
        val songs = listOf(
            song(id = 10, title = "A", path = stored),
            song(id = 11, title = "B", path = stored),
        )

        val result = SongFileIntegrity.scan(songs)

        assertTrue(result.hasIssues)
        assertEquals(2, result.missingSongs.size)
        assertEquals(1, result.sharedFilePaths.size)
        assertFalse(result.sharedFilePaths.single().fileExists)
    }

    private fun writeSongFile(name: String, bytes: ByteArray): String {
        val file = File(songsDir, name)
        file.writeBytes(bytes)
        return SongStoragePaths.toStoredPath(file)
    }

    private fun song(id: Long, title: String, path: String) = Song(
        id = id,
        title = title,
        keySignature = "",
        notes = "",
        filePath = path,
        fileType = FileType.PDF.name,
    )
}
