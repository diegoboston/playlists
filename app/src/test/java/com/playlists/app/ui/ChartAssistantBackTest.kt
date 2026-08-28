package com.playlists.app.ui

import com.playlists.app.ai.ChartDraft
import com.playlists.app.ai.ChartIntent
import com.playlists.app.ai.ChartSearchMode
import com.playlists.app.ai.ChartSection
import com.playlists.app.find.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChartAssistantBackTest {
    private val intent = ChartIntent(
        action = "find_chart",
        songTitle = "Lean on Me",
        artist = "Bill Withers",
        key = null,
        playlistName = null,
        searchMode = ChartSearchMode.ChordsAndLyrics,
    )
    private val savedSearch = ChartAssistantUiState.SearchResults(
        intent = intent,
        playlist = null,
        results = listOf(
            SearchResult("Ultimate Guitar", "https://example.com/chart", "chords"),
        ),
    )

    @Test
    fun previewWithSavedSearch_restoresResults() {
        val next = chartAssistantStateAfterBack(
            state = previewState(),
            savedSearch = savedSearch,
            workInFlight = false,
        )
        assertEquals(savedSearch, next)
    }

    @Test
    fun previewWithoutSavedSearch_staysIdle() {
        val next = chartAssistantStateAfterBack(
            state = previewState(),
            savedSearch = null,
            workInFlight = false,
        )
        assertEquals(ChartAssistantUiState.Idle, next)
    }

    @Test
    fun processingWithInFlightExtract_restoresResults() {
        val next = chartAssistantStateAfterBack(
            state = ChartAssistantUiState.Processing,
            savedSearch = savedSearch,
            workInFlight = true,
        )
        assertEquals(savedSearch, next)
    }

    @Test
    fun processingWhileSaving_leavesAssistant() {
        assertNull(
            chartAssistantStateAfterBack(
                state = ChartAssistantUiState.Processing,
                savedSearch = savedSearch,
                workInFlight = false,
            ),
        )
    }

    @Test
    fun extractError_restoresResultsWithMessage() {
        val next = chartAssistantStateAfterBack(
            state = ChartAssistantUiState.Error("Could not extract chart from page"),
            savedSearch = savedSearch,
            workInFlight = false,
        )
        assertEquals(
            savedSearch.copy(errorMessage = "Could not extract chart from page"),
            next,
        )
    }

    @Test
    fun extractFailure_dropsUnparseableResult() {
        val failed = SearchResult("Bad page", "https://example.com/bad", "")
        val keep = SearchResult("Good page", "https://example.com/good", "chords")
        val search = savedSearch.copy(results = listOf(failed, keep))
        val next = chartAssistantStateAfterExtractFailure(
            savedSearch = search,
            message = "Could not extract chart from page",
            failedUrl = failed.url,
        )
        assertEquals(
            search.copy(results = listOf(keep), errorMessage = "Could not extract chart from page"),
            next,
        )
    }

    @Test
    fun extractFailure_withoutSavedSearch_isError() {
        assertEquals(
            ChartAssistantUiState.Error("Could not extract chart from page"),
            chartAssistantStateAfterExtractFailure(
                savedSearch = null,
                message = "Could not extract chart from page",
                failedUrl = "https://example.com/bad",
            ),
        )
    }

    @Test
    fun searchResults_leavesAssistant() {
        assertNull(
            chartAssistantStateAfterBack(
                state = savedSearch,
                savedSearch = savedSearch,
                workInFlight = false,
            ),
        )
    }

    @Test
    fun idle_leavesAssistant() {
        assertNull(
            chartAssistantStateAfterBack(
                state = ChartAssistantUiState.Idle,
                savedSearch = savedSearch,
                workInFlight = false,
            ),
        )
    }

    private fun previewState(): ChartAssistantUiState.Preview {
        val draft = ChartDraft(
            title = "Lean on Me",
            artist = "Bill Withers",
            sourceKey = "C",
            key = "C",
            capo = null,
            columns = 1,
            sections = listOf(ChartSection("Verse", listOf("line"))),
            notes = null,
            sourceUrl = "https://example.com/chart",
        )
        val pdf = File.createTempFile("chart-preview-test", ".pdf")
        pdf.deleteOnExit()
        return ChartAssistantUiState.Preview(
            intent = intent,
            playlist = null,
            sourceDraft = draft,
            draft = draft,
            pdfFile = pdf,
            transposeNote = null,
        ).also { assertTrue(it.pdfFile.exists()) }
    }
}
