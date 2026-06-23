package com.playlists.app.util

import com.playlists.app.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun song(id: Long, filePath: String) = Song(
        id = id,
        title = "Title",
        keySignature = "",
        notes = "",
        filePath = filePath,
        fileType = "PDF",
        mimeType = "application/pdf",
    )
}
