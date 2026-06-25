package com.playlists.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.playlists.app.PlaylistsApp
import com.playlists.app.ai.ChartAssistantException
import com.playlists.app.ai.ChartAssistantService
import com.playlists.app.ai.ChartDraft
import com.playlists.app.ai.ChartIntent
import com.playlists.app.ai.OpenAiClient
import com.playlists.app.ai.OpenAiException
import com.playlists.app.ai.PlaylistNameResolver
import com.playlists.app.ai.AiJsonHelper
import com.playlists.app.find.WebSearchService
import com.playlists.app.data.FileType
import com.playlists.app.data.Playlist
import com.playlists.app.data.Song
import com.playlists.app.find.SearchResult
import com.playlists.app.util.AiCredentialStore
import com.playlists.app.util.AudioRecorder
import com.playlists.app.render.ChartPdfRenderer
import com.playlists.app.render.ChordTransposer
import com.playlists.app.util.ChartDraftStore
import com.playlists.app.util.FileStorage
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

sealed class ChartAssistantUiState {
    data object Idle : ChartAssistantUiState()
    data object Recording : ChartAssistantUiState()
    data object Processing : ChartAssistantUiState()
    data class IntentReady(
        val intent: ChartIntent,
        val playlist: Playlist,
    ) : ChartAssistantUiState()
    data class SearchResults(
        val intent: ChartIntent,
        val playlist: Playlist,
        val results: List<SearchResult>,
    ) : ChartAssistantUiState()
    data class Preview(
        val intent: ChartIntent,
        val playlist: Playlist,
        val sourceDraft: ChartDraft,
        val draft: ChartDraft,
        val semitoneOffset: Int = 0,
        val pdfFile: File,
        val transposeNote: String?,
        val previewRevision: Int = 0,
    ) : ChartAssistantUiState()
    data class Error(val message: String) : ChartAssistantUiState()
}

class ChartAssistantViewModel(
    app: Application,
    private val playlistId: Long,
) : AndroidViewModel(app) {
    private val playlistsApp = PlaylistsApp.from(app)
    private val playlistRepo = playlistsApp.playlistRepository
    private val songRepo = playlistsApp.songRepository
    private val audioRecorder = AudioRecorder(app)

    private val _uiState = MutableStateFlow<ChartAssistantUiState>(ChartAssistantUiState.Idle)
    val uiState: StateFlow<ChartAssistantUiState> = _uiState.asStateFlow()

    private val _savedSongId = MutableSharedFlow<Long>()
    val savedSongId: SharedFlow<Long> = _savedSongId.asSharedFlow()

    fun startRecording() {
        if (_uiState.value is ChartAssistantUiState.Recording) return
        runCatching { audioRecorder.start() }
            .onSuccess { _uiState.value = ChartAssistantUiState.Recording }
            .onFailure { _uiState.value = ChartAssistantUiState.Error(it.message ?: "Mic failed") }
    }

    fun stopRecordingAndProcess() {
        if (_uiState.value !is ChartAssistantUiState.Recording) return
        val audioFile = audioRecorder.stop()
        if (audioFile == null) {
            _uiState.value = ChartAssistantUiState.Error("No audio recorded")
            return
        }
        _uiState.value = ChartAssistantUiState.Processing
        viewModelScope.launch {
            processAudio(audioFile)
        }
    }

    fun cancelRecording() {
        audioRecorder.stop()
        _uiState.value = ChartAssistantUiState.Idle
    }

    fun retrySearch() {
        val state = _uiState.value
        if (state is ChartAssistantUiState.SearchResults || state is ChartAssistantUiState.IntentReady) {
            val intent = when (state) {
                is ChartAssistantUiState.SearchResults -> state.intent
                is ChartAssistantUiState.IntentReady -> state.intent
                else -> return
            }
            val playlist = when (state) {
                is ChartAssistantUiState.SearchResults -> state.playlist
                is ChartAssistantUiState.IntentReady -> state.playlist
                else -> return
            }
            searchForIntent(intent, playlist)
        }
    }

    fun selectSearchResult(result: SearchResult) {
        val state = _uiState.value as? ChartAssistantUiState.SearchResults ?: return
        _uiState.value = ChartAssistantUiState.Processing
        viewModelScope.launch {
            extractFromResult(state.intent, state.playlist, result)
        }
    }

    fun confirmSave() {
        val state = _uiState.value as? ChartAssistantUiState.Preview ?: return
        _uiState.value = ChartAssistantUiState.Processing
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    saveChart(state)
                }
            }.onSuccess { songId ->
                _savedSongId.emit(songId)
            }.onFailure {
                _uiState.value = ChartAssistantUiState.Error(it.message ?: "Save failed")
            }
        }
    }

    fun dismissError() {
        _uiState.value = ChartAssistantUiState.Idle
    }

    fun cancelPreview() {
        val state = _uiState.value as? ChartAssistantUiState.Preview ?: return
        state.pdfFile.delete()
        _uiState.value = ChartAssistantUiState.Idle
    }

    fun nudgePreviewKey(semitones: Int) {
        if (semitones == 0) return
        val state = _uiState.value as? ChartAssistantUiState.Preview ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val newOffset = state.semitoneOffset + semitones
                    val newDraft = draftAtOffset(state.sourceDraft, newOffset)
                    val pdfBytes = ChartPdfRenderer.render(newDraft)
                    state.pdfFile.writeBytes(pdfBytes)
                    val transposeNote = newDraft.sourceKey?.takeIf { source ->
                        newDraft.key != null && !source.equals(newDraft.key, ignoreCase = true)
                    }?.let { source -> "Source: $source → ${newDraft.key}" }
                    state.copy(
                        draft = newDraft,
                        semitoneOffset = newOffset,
                        transposeNote = transposeNote,
                        previewRevision = state.previewRevision + 1,
                    )
                }
            }.onSuccess { updated ->
                _uiState.value = updated
            }.onFailure {
                _uiState.value = ChartAssistantUiState.Error(it.message ?: "Transpose failed")
            }
        }
    }

    private fun draftAtOffset(source: ChartDraft, offset: Int): ChartDraft =
        if (offset == 0) source else ChordTransposer.transposeBySemitones(source, offset)

    private suspend fun processAudio(audioFile: File) {
        val context = getApplication<Application>()
        val apiKey = AiCredentialStore.getOpenAiApiKey(context)
        if (apiKey == null) {
            _uiState.value = ChartAssistantUiState.Error("Add OpenAI API key in Settings")
            audioFile.delete()
            return
        }
        runCatching {
            withContext(Dispatchers.IO) {
                val client = OpenAiClient(apiKey)
                val playlists = playlistRepo.getAll()
                val transcript = client.transcribeAudio(audioFile)
                if (transcript.isBlank()) {
                    throw ChartAssistantException("Could not understand audio")
                }
                val intent = client.parseIntent(
                    transcript = transcript,
                    playlistsContextJson = AiJsonHelper.playlistsContext(playlists, playlistId),
                ) ?: throw ChartAssistantException("Could not parse command")
                val playlist = PlaylistNameResolver.resolve(
                    name = intent.playlistName,
                    playlists = playlists,
                    defaultPlaylistId = playlistId,
                ) ?: throw ChartAssistantException("Playlist not found")
                intent.copy(transcript = transcript) to playlist
            }
        }.onSuccess { (intent, playlist) ->
            audioFile.delete()
            _uiState.value = ChartAssistantUiState.IntentReady(intent, playlist)
            searchForIntent(intent, playlist)
        }.onFailure { error ->
            audioFile.delete()
            _uiState.value = ChartAssistantUiState.Error(error.userMessage())
        }
    }

    private fun searchForIntent(intent: ChartIntent, playlist: Playlist) {
        _uiState.value = ChartAssistantUiState.Processing
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    WebSearchService.search(intent.searchQuery())
                }
            }.onSuccess { results ->
                if (results.isEmpty()) {
                    _uiState.value = ChartAssistantUiState.Error("No search results")
                } else {
                    _uiState.value = ChartAssistantUiState.SearchResults(intent, playlist, results)
                }
            }.onFailure {
                _uiState.value = ChartAssistantUiState.Error(it.message ?: "Search failed")
            }
        }
    }

    private suspend fun extractFromResult(
        intent: ChartIntent,
        playlist: Playlist,
        result: SearchResult,
    ) {
        val context = getApplication<Application>()
        val apiKey = AiCredentialStore.getOpenAiApiKey(context)
            ?: run {
                _uiState.value = ChartAssistantUiState.Error("Add OpenAI API key in Settings")
                return
            }
        runCatching {
            withContext(Dispatchers.IO) {
                val service = ChartAssistantService(OpenAiClient(apiKey))
                val (draft, pdfBytes) = service.fetchAndBuildChart(result, intent)
                val previewFile = File(context.cacheDir, "chart-preview-${System.currentTimeMillis()}.pdf")
                previewFile.writeBytes(pdfBytes)
                val transposeNote = draft.sourceKey?.takeIf { source ->
                    draft.key != null && !source.equals(draft.key, ignoreCase = true)
                }?.let { source -> "Source: $source → ${draft.key}" }
                draft to PreviewBundle(previewFile, transposeNote)
            }
        }.onSuccess { (draft, bundle) ->
            _uiState.value = ChartAssistantUiState.Preview(
                intent = intent,
                playlist = playlist,
                sourceDraft = draft,
                draft = draft,
                semitoneOffset = 0,
                pdfFile = bundle.file,
                transposeNote = bundle.transposeNote,
            )
        }.onFailure {
            _uiState.value = ChartAssistantUiState.Error(it.userMessage())
        }
    }

    private suspend fun saveChart(state: ChartAssistantUiState.Preview): Long {
        val draft = state.draft
        val key = draft.key.orEmpty()
        val notes = buildNotes(draft)
        val storedFile = FileStorage.storeBytes(state.pdfFile.readBytes(), "pdf")
        val storedPath = SongStoragePaths.toStoredPath(storedFile)
        ChartDraftStore.save(state.sourceDraft, storedPath)
        state.pdfFile.delete()
        val songId = songRepo.insert(
            Song(
                title = draft.title,
                keySignature = key,
                notes = notes,
                filePath = storedPath,
                fileType = FileType.PDF.name,
            ),
        )
        playlistRepo.addSong(state.playlist.id, songId)
        return songId
    }

    private fun buildNotes(draft: ChartDraft): String {
        return buildList {
            add("AI chart")
            draft.sourceUrl?.let { add(it) }
            draft.artist?.let { add(it) }
        }.joinToString(" · ")
    }

    private fun Throwable.userMessage(): String = when (this) {
        is OpenAiException -> message ?: "OpenAI error"
        is ChartAssistantException -> message ?: "Chart assistant error"
        else -> message ?: "Something went wrong"
    }

    private data class PreviewBundle(val file: File, val transposeNote: String?)
}
