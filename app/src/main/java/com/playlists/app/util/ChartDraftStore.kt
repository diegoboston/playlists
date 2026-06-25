package com.playlists.app.util

import com.playlists.app.ai.ChartDraft
import org.json.JSONObject
import java.io.File

/**
 * Sidecar JSON for AI-generated charts: `{pdfBase}.chart.json` next to the PDF.
 * Stores the source-key lyrics/chords so the chart can be re-transposed later.
 */
object ChartDraftStore {
    const val CHART_EXTENSION = "chart.json"

    fun chartFileName(pdfFileName: String): String {
        val base = pdfFileName.substringBeforeLast('.').ifBlank { pdfFileName }
        return "$base.$CHART_EXTENSION"
    }

    fun chartStoredPath(pdfStoredPath: String): String {
        val pdfName = SongStoragePaths.fileName(pdfStoredPath)
        return "${SongStoragePaths.SONGS_RELATIVE_DIR}/${chartFileName(pdfName)}"
    }

    fun resolveChartFile(pdfStoredPath: String): File {
        val name = chartFileName(SongStoragePaths.fileName(pdfStoredPath))
        return File(StageManagerStorage.songsDir(), name)
    }

    fun hasChart(pdfStoredPath: String): Boolean = resolveChartFile(pdfStoredPath).isFile

    fun save(sourceDraft: ChartDraft, pdfStoredPath: String): File {
        val file = resolveChartFile(pdfStoredPath)
        file.writeText(sourceDraft.toJson().toString(2))
        return file
    }

    fun load(pdfStoredPath: String): ChartDraft? {
        val file = resolveChartFile(pdfStoredPath)
        if (!file.isFile) return null
        return runCatching {
            ChartDraft.fromJson(JSONObject(file.readText()))
        }.getOrNull()
    }

    fun deleteIfPresent(pdfStoredPath: String) {
        resolveChartFile(pdfStoredPath).delete()
    }
}
