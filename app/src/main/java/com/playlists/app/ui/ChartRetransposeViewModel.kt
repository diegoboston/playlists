package com.playlists.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.playlists.app.ai.ChartDraft
import com.playlists.app.data.Song
import com.playlists.app.render.ChartPdfRenderer
import com.playlists.app.render.ChordTransposer
import com.playlists.app.util.ChartDraftStore
import com.playlists.app.util.SongStoragePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class ChartRetransposeUiState {
    data object Loading : ChartRetransposeUiState()
    data class Preview(
        val song: Song,
        val sourceDraft: ChartDraft,
        val draft: ChartDraft,
        val semitoneOffset: Int,
        val pdfFile: File,
        val transposeNote: String?,
        val previewRevision: Int = 0,
    ) : ChartRetransposeUiState()
    data class Error(val message: String) : ChartRetransposeUiState()
}

class ChartRetransposeViewModel(
    app: Application,
    private val songId: Long,
    private val songRepo: com.playlists.app.data.SongRepository,
) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow<ChartRetransposeUiState>(ChartRetransposeUiState.Loading)
    val uiState: StateFlow<ChartRetransposeUiState> = _uiState.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun nudgePreviewKey(semitones: Int) {
        if (semitones == 0) return
        val state = _uiState.value as? ChartRetransposeUiState.Preview ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val newOffset = state.semitoneOffset + semitones
                    val newDraft = draftAtOffset(state.sourceDraft, newOffset)
                    val pdfBytes = ChartPdfRenderer.render(newDraft)
                    state.pdfFile.writeBytes(pdfBytes)
                    state.copy(
                        draft = newDraft,
                        semitoneOffset = newOffset,
                        transposeNote = null,
                        previewRevision = state.previewRevision + 1,
                    )
                }
            }.onSuccess { updated ->
                _uiState.value = updated
            }.onFailure {
                _uiState.value = ChartRetransposeUiState.Error(it.message ?: "Transpose failed")
            }
        }
    }

    fun confirmSave() {
        val state = _uiState.value as? ChartRetransposeUiState.Preview ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val pdfFile = SongStoragePaths.resolve(state.song.filePath)
                    pdfFile.writeBytes(state.pdfFile.readBytes())
                    songRepo.update(
                        state.song.copy(keySignature = state.draft.key.orEmpty()),
                    )
                }
            }.onSuccess {
                state.pdfFile.delete()
                _saved.emit(Unit)
            }.onFailure {
                _uiState.value = ChartRetransposeUiState.Error(it.message ?: "Save failed")
            }
        }
    }

    fun cancelPreview() {
        val state = _uiState.value as? ChartRetransposeUiState.Preview ?: return
        state.pdfFile.delete()
    }

    private suspend fun load() {
        runCatching {
            withContext(Dispatchers.IO) {
                val song = songRepo.getById(songId)
                    ?: throw IllegalStateException("Song not found")
                val sourceDraft = ChartDraftStore.load(song.filePath)
                    ?: throw IllegalStateException("No chart source for this song")
                val currentKey = song.keySignature.trim().ifBlank {
                    sourceDraft.sourceKey ?: sourceDraft.key ?: "C"
                }
                val sourceKey = sourceDraft.sourceKey ?: sourceDraft.key ?: "C"
                val offset = ChordTransposer.semitonesBetween(sourceKey, currentKey) ?: 0
                val displayDraft = draftAtOffset(sourceDraft, offset)
                val context = getApplication<Application>()
                val previewFile = File(context.cacheDir, "chart-retranspose-${System.currentTimeMillis()}.pdf")
                previewFile.writeBytes(ChartPdfRenderer.render(displayDraft))
                ChartRetransposeUiState.Preview(
                    song = song,
                    sourceDraft = sourceDraft,
                    draft = displayDraft,
                    semitoneOffset = offset,
                    pdfFile = previewFile,
                    transposeNote = null,
                )
            }
        }.onSuccess { preview ->
            _uiState.value = preview
        }.onFailure {
            _uiState.value = ChartRetransposeUiState.Error(it.message ?: "Could not load chart")
        }
    }

    private fun draftAtOffset(source: ChartDraft, offset: Int): ChartDraft =
        if (offset == 0) source else ChordTransposer.transposeBySemitones(source, offset)
}

class ChartRetransposeViewModelFactory(
    private val app: Application,
    private val songId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChartRetransposeViewModel::class.java)) {
            val playlistsApp = com.playlists.app.PlaylistsApp.from(app)
            return ChartRetransposeViewModel(app, songId, playlistsApp.songRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
