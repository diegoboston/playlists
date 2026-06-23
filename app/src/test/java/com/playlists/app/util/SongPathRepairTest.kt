package com.playlists.app.util

import com.playlists.app.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SongPathRepairTest {
    @Test
    fun repairPath_returnsNullWhenResolvedDbPathExists() {
        val root = createTempDir(prefix = "repair-ok")
        val songsDir = File(root, "songs").apply { mkdirs() }
        File(songsDir, "linked.pdf").apply { writeText("x") }
        val stored = "Music/StageManager/songs/linked.pdf"
        val song = song(id = 1, filePath = stored)

        assertNull(SongPathRepair.repairPath(song, songsDir))
    }

    @Test
    fun repairPath_normalizesAbsolutePathWhenFileExists() {
        val root = createTempDir(prefix = "repair-norm")
        val songsDir = File(root, "songs").apply { mkdirs() }
        File(songsDir, "linked.pdf").apply { writeText("x") }
        val song = song(id = 1, filePath = "/sdcard/Music/StageManager/songs/linked.pdf")

        assertEquals(
            "Music/StageManager/songs/linked.pdf",
            SongPathRepair.repairPath(song, songsDir),
        )
    }

    @Test
    fun repairPath_findsFileByBasenameWhenDbPathIsStale() {
        val root = createTempDir(prefix = "repair-base")
        val songsDir = File(root, "songs").apply { mkdirs() }
        File(songsDir, "Se_Telefonando--30.png").apply { writeText("x") }
        val song = song(id = 30, filePath = "/data/user/0/app/files/songs/old-uuid.png")

        assertEquals(
            "Music/StageManager/songs/Se_Telefonando--30.png",
            SongPathRepair.repairPath(song, songsDir),
        )
    }

    @Test
    fun repairPath_findsFileBySongIdSuffix() {
        val root = createTempDir(prefix = "repair-id")
        val songsDir = File(root, "songs").apply { mkdirs() }
        File(songsDir, "E_penso_a_te-CD-3.pdf").apply { writeText("x") }
        val song = song(id = 3, filePath = "Music/StageManager/songs/missing.pdf")

        assertEquals(
            "Music/StageManager/songs/E_penso_a_te-CD-3.pdf",
            SongPathRepair.repairPath(song, songsDir),
        )
    }

    @Test
    fun repairAll_linksPlaceholderUuidFileWhenDbPathIsWrong() {
        val root = createTempDir(prefix = "repair-placeholder")
        val songsDir = File(root, "songs").apply { mkdirs() }
        val uuidFile = File(songsDir, "550e8400-e29b-41d4-a716-446655440000.png").apply { writeText("x") }
        val song = song(
            id = 30,
            filePath = "Music/StageManager/songs/Se_Telefonando--30.png",
            isPlaceholder = true,
            fileType = "IMAGE",
        )

        val updates = SongPathRepair.repairAll(listOf(song), songsDir)

        assertEquals(
            "Music/StageManager/songs/${uuidFile.name}",
            updates[30],
        )
    }

    @Test
    fun repairAll_doesNotTreatLinkedPlaceholderAsOrphan() {
        val root = createTempDir(prefix = "repair-orphan")
        val songsDir = File(root, "songs").apply { mkdirs() }
        val uuidFile = File(songsDir, "550e8400-e29b-41d4-a716-446655440000.png").apply { writeText("x") }
        File(songsDir, "real-orphan.pdf").writeText("y")
        val song = song(
            id = 30,
            filePath = "Music/StageManager/songs/Se_Telefonando--30.png",
            isPlaceholder = true,
            fileType = "IMAGE",
        )
        val updates = SongPathRepair.repairAll(listOf(song), songsDir)
        val referenced = listOf(updates[30]!!)

        val orphans = OrphanSongFiles.findOrphans(songsDir, referenced)

        assertEquals(listOf("real-orphan.pdf"), orphans.map { it.name })
        assertTrue(orphans.none { it.name == uuidFile.name })
    }

    private fun song(
        id: Long,
        filePath: String,
        isPlaceholder: Boolean = false,
        fileType: String = "PDF",
    ) = Song(
        id = id,
        title = "Title",
        keySignature = "",
        notes = "",
        filePath = filePath,
        fileType = fileType,
        mimeType = if (fileType == "PDF") "application/pdf" else "image/png",
        isPlaceholder = isPlaceholder,
    )
}
