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
    fun preview_leavesAssistant() {
        assertNull(
            chartAssistantStateAfterBack(
                state = previewState(),
                workInFlight = false,
            ),
        )
    }

    @Test
    fun processingWithInFlightExtract_goesIdle() {
        assertEquals(
            ChartAssistantUiState.Idle,
            chartAssistantStateAfterBack(
                state = ChartAssistantUiState.Processing,
                workInFlight = true,
            ),
        )
    }

    @Test
    fun processingWhileSaving_leavesAssistant() {
        assertNull(
            chartAssistantStateAfterBack(
                state = ChartAssistantUiState.Processing,
                workInFlight = false,
            ),
        )
    }

    @Test
    fun extractError_returnsToIdle() {
        assertEquals(
            ChartAssistantUiState.Idle,
            chartAssistantStateAfterBack(
                state = ChartAssistantUiState.Error("Could not extract chart from page"),
                workInFlight = false,
            ),
        )
    }

    @Test
    fun searchResults_leavesAssistant() {
        assertNull(
            chartAssistantStateAfterBack(
                state = savedSearch,
                workInFlight = false,
            ),
        )
    }

    @Test
    fun idle_leavesAssistant() {
        assertNull(
            chartAssistantStateAfterBack(
                state = ChartAssistantUiState.Idle,
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
