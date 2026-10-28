package com.playlists.app.remote

import com.playlists.app.ai.ChartDraft
import com.playlists.app.ai.ChartSearchMode
import com.playlists.app.ai.ChartSection
import com.playlists.app.find.SearchResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteChartSearchTest {
    private val draft = ChartDraft("Test song", "Artist", "C", "C", null, 1,
        listOf(ChartSection("Verse", listOf("<C>Hello"))), null, "https://example.com")
    private fun body(token: String) = JSONObject().put("token", token)
    private fun awaitState(search: RemoteChartSearch, token: String, expected: String): JSONObject {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val status = search.request(1, "status", body(token))
            if (status.getString("state") == expected) return status
            if (status.getString("state") == "error") fail(status.toString())
            Thread.sleep(5)
        }
        error("Timed out waiting for $expected")
    }

    @Test fun searchProcessesFiveResultsAndTryNextAdvancesToTheNextBatch() {
        val saves = AtomicInteger()
        val saving = CountDownLatch(1)
        val releaseSave = CountDownLatch(1)
        val search = RemoteChartSearch(
            search = { intent, offset ->
                assertEquals("Volare", intent.songTitle)
                assertEquals(ChartSearchMode.LyricsOnly, intent.searchMode)
                assertTrue(offset == 0 || offset == 5)
                (1..(if (offset == 0) 5 else 3)).map { n ->
                    val result = n + offset
                    SearchResult("Result $result", "https://example.com/$result", "")
                }
            },
            extract = { results, _ ->
                assertTrue(results.size in 3..5)
                draft.copy(title = results.first().title)
            },
            render = { byteArrayOf(1, 2, 3) },
            save = { id, chart, pdf, mode ->
                assertEquals(1L, id)
                assertEquals("Result 6", chart.title)
                assertArrayEquals(byteArrayOf(1, 2, 3), pdf)
                assertEquals(ChartSearchMode.LyricsOnly, mode)
                saves.incrementAndGet()
                saving.countDown()
                assertTrue(releaseSave.await(5, TimeUnit.SECONDS))
            },
            available = { true }, playlistExists = { it == 1L },
        )
        try {
            val token = search.request(1, "start", JSONObject().put("query", "Volare").put("mode", "lyrics")).getString("token")
            val preview = awaitState(search, token, "preview")
            assertEquals("Result 1", preview.getString("title"))
            assertTrue(preview.getBoolean("hasNext"))
            assertArrayEquals(byteArrayOf(1, 2, 3), search.preview(1, token))
            assertNull(search.preview(2, token))
            assertThrows(IllegalStateException::class.java) { search.request(2, "save", body(token)) }
            search.request(1, "next", body(token))
            assertFalse(awaitState(search, token, "preview").getBoolean("hasNext"))
            search.request(1, "save", body(token))
            assertTrue(saving.await(5, TimeUnit.SECONDS))
            assertEquals("saving", search.request(1, "save", body(token)).getString("state"))
            assertThrows(IllegalStateException::class.java) { search.request(1, "cancel", body(token)) }
            releaseSave.countDown()
            awaitState(search, token, "saved")
            assertEquals("saved", search.request(1, "save", body(token)).getString("state"))
            assertEquals(1, saves.get())
            assertNull(search.preview(1, token))
        } finally {
            releaseSave.countDown()
            search.close()
        }
    }

    @Test fun pastedTextBypassesSearchAndCanBePreviewedAndSaved() {
        val saves = AtomicInteger()
        val raw = "Test song\nAdvertisement\n[Verse]\nHello"
        val search = RemoteChartSearch(
            search = { _, _ -> error("Text import must not search") },
            extract = { _, _ -> error("Text import must not fetch pages") },
            extractText = { text, mode ->
                assertEquals(raw, text)
                assertEquals(ChartSearchMode.LyricsOnly, mode)
                draft.copy(sourceUrl = null)
            },
            render = { byteArrayOf(4, 5) },
            save = { id, chart, pdf, mode ->
                assertEquals(1L, id)
                assertNull(chart.sourceUrl)
                assertArrayEquals(byteArrayOf(4, 5), pdf)
                assertEquals(ChartSearchMode.LyricsOnly, mode)
                saves.incrementAndGet()
            },
            available = { true }, playlistExists = { true },
        )
        try {
            for (text in listOf(" ", "x".repeat(30_001))) {
                assertThrows(IllegalArgumentException::class.java) {
                    search.request(1, "start", JSONObject().put("input", "text").put("text", text))
                }
            }
            val token = search.request(1, "start", JSONObject()
                .put("input", "text").put("text", raw).put("mode", "lyrics")).getString("token")
            val preview = awaitState(search, token, "preview")
            assertFalse(preview.getBoolean("hasNext"))
            assertEquals("Test song", preview.getString("title"))
            assertThrows(IllegalStateException::class.java) { search.request(1, "next", body(token)) }
            search.request(1, "save", body(token))
            awaitState(search, token, "saved")
            assertEquals(1, saves.get())
        } finally { search.close() }
    }

    @Test fun validatesCredentialsAndInputBeforeStartingWork() {
        var available = false
        val search = RemoteChartSearch(
            search = { _, _ -> error("Must not search") }, extract = { _, _ -> error("Must not extract") },
            render = { error("Must not render") }, save = { _, _, _, _ -> error("Must not save") },
            available = { available }, playlistExists = { true },
        )
        try {
            assertFalse(search.request(1, "available", JSONObject()).getBoolean("available"))
            assertThrows(IllegalStateException::class.java) { search.request(1, "start", JSONObject().put("query", "Song")) }
            available = true
            assertThrows(IllegalArgumentException::class.java) { search.request(1, "start", JSONObject().put("query", " ")) }
            assertThrows(IllegalStateException::class.java) { search.request(1, "start", JSONObject().put("query", "Song").put("mode", "bad")) }
        } finally { search.close() }
    }

    @Test fun completedSearchesDoNotUseUpTheActiveSearchLimit() {
        val search = RemoteChartSearch(
            search = { _, _ -> listOf(SearchResult("Result", "https://example.com", "")) },
            extract = { _, _ -> draft }, render = { byteArrayOf(1) },
            save = { _, _, _, _ -> }, available = { true }, playlistExists = { true },
        )
        try {
            repeat(9) {
                val token = search.request(1, "start", JSONObject().put("query", "Song")).getString("token")
                awaitState(search, token, "preview")
                search.request(1, "save", body(token))
                awaitState(search, token, "saved")
            }
        } finally { search.close() }
    }

    @Test fun cancelledSearchCannotBePreviewedOrSaved() {
        val search = RemoteChartSearch(
            search = { _, _ -> listOf(SearchResult("Result", "https://example.com", "")) },
            extract = { _, _ -> draft }, render = { byteArrayOf(1) },
            save = { _, _, _, _ -> fail("Cancelled search must not save") },
            available = { true }, playlistExists = { true },
        )
        try {
            val token = search.request(1, "start", JSONObject().put("query", "Song")).getString("token")
            awaitState(search, token, "preview")
            search.request(1, "cancel", body(token))
            assertNull(search.preview(1, token))
            assertThrows(IllegalStateException::class.java) { search.request(1, "save", body(token)) }
        } finally { search.close() }
    }
}
