package com.playlists.app.util

import com.playlists.app.ai.ChartDraft
import com.playlists.app.ai.ChartSection
import com.playlists.app.data.FileType
import com.playlists.app.data.Song
import com.playlists.app.data.SongDao
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SongFileMigrationTest {
    private lateinit var songsDir: File
    private lateinit var songDao: FakeSongDao

    @Before
    fun setUp() {
        songsDir = File.createTempFile("song-migration-", "").apply {
            delete()
            mkdirs()
        }
        StageManagerStorage.setSongsDirForTests(songsDir)
        songDao = FakeSongDao()
    }

    @After
    fun tearDown() {
        StageManagerStorage.setSongsDirForTests(null)
        songsDir.deleteRecursively()
    }

    @Test
    fun migrate_renamesUuidFileAndSidecar() = runBlocking {
        val uuidName = "a1b2c3d4-e5f6-7890-abcd-ef1234567890.pdf"
        val uuidFile = File(songsDir, uuidName)
        uuidFile.writeBytes(byteArrayOf(1, 2, 3))
        val stored = SongStoragePaths.toStoredPath(uuidFile)
        ChartDraftStore.save(sampleDraft(), stored)

        val song = Song(
            id = 42,
            title = "Amazing Grace",
            keySignature = "G",
            notes = "",
            filePath = stored,
            fileType = FileType.PDF.name,
        )
        songDao.seed(song)

        SongFileMigration.migrate(songDao)

        val expectedMedia = File(songsDir, "Amazing_Grace-42.pdf")
        val expectedSidecar = File(songsDir, "Amazing_Grace-42.chart.json")
        assertTrue(expectedMedia.isFile)
        assertTrue(expectedSidecar.isFile)
        assertFalse(uuidFile.exists())
        assertEquals(SongStoragePaths.toStoredPath(expectedMedia), songDao.getAll().single().filePath)
    }

    @Test
    fun deleteOrphanUuidFiles_removesUnreferencedUuidFiles() = runBlocking {
        val orphan = File(songsDir, "b1b2c3d4-e5f6-7890-abcd-ef1234567890.png")
        orphan.writeBytes(byteArrayOf(9))
        val sidecar = File(songsDir, "b1b2c3d4-e5f6-7890-abcd-ef1234567890.chart.json")
        sidecar.writeText("{}")

        SongFileMigration.deleteOrphanUuidFiles(songDao)

        assertFalse(orphan.exists())
        assertFalse(sidecar.exists())
    }

    @Test
    fun migrate_copiesSharedSourceForEachSong() = runBlocking {
        val shared = File(songsDir, "a1b2c3d4-e5f6-7890-abcd-ef1234567890.pdf")
        shared.writeBytes(byteArrayOf(5))
        val stored = SongStoragePaths.toStoredPath(shared)
        songDao.seed(
            Song(id = 1, title = "Song A", keySignature = "", notes = "", filePath = stored, fileType = FileType.PDF.name),
            Song(id = 2, title = "Song B", keySignature = "", notes = "", filePath = stored, fileType = FileType.PDF.name),
        )

        SongFileMigration.migrate(songDao)

        assertTrue(File(songsDir, "Song_A-1.pdf").isFile)
        assertTrue(File(songsDir, "Song_B-2.pdf").isFile)
        assertFalse(shared.exists())
    }

    private fun sampleDraft() = ChartDraft(
        title = "Amazing Grace",
        artist = null,
        sourceKey = "G",
        key = "G",
        capo = null,
        columns = 1,
        sections = listOf(ChartSection("Verse", listOf("G C D"))),
        notes = null,
        sourceUrl = null,
    )

    private class FakeSongDao : SongDao {
        private val songs = linkedMapOf<Long, Song>()

        fun seed(vararg items: Song) {
            items.forEach { songs[it.id] = it }
        }

        override fun observeAll() = throw UnsupportedOperationException()
        override suspend fun getAll(): List<Song> = songs.values.toList()
        override suspend fun getById(id: Long): Song? = songs[id]
        override suspend fun insert(song: Song): Long = throw UnsupportedOperationException()
        override suspend fun update(song: Song) {
            songs[song.id] = song
        }
        override suspend fun deleteById(id: Long) = throw UnsupportedOperationException()
        override suspend fun updateLastViewedAt(id: Long, viewedAt: Long) =
            throw UnsupportedOperationException()
        override suspend fun search(query: String) = throw UnsupportedOperationException()
        override suspend fun updateSortOrder(id: Long, order: Int) =
            throw UnsupportedOperationException()
        override suspend fun incrementAllSortOrders() = throw UnsupportedOperationException()
        override suspend fun insertAtTop(song: Song): Long = throw UnsupportedOperationException()
        override suspend fun replaceOrder(idsInOrder: List<Long>) =
            throw UnsupportedOperationException()
    }
}
