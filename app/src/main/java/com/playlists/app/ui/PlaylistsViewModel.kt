package com.playlists.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playlists.app.PlaylistsApp
import com.playlists.app.data.FileType
import com.playlists.app.data.Playlist
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.data.Song
import com.playlists.app.util.AppUpdate
import com.playlists.app.util.PendingImport
import com.playlists.app.util.QuickstartMatcher
import com.playlists.app.util.ShareImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {
    private val songRepo = PlaylistsApp.from(app).songRepository
    private val playlistRepo = PlaylistsApp.from(app).playlistRepository

    val songs: StateFlow<List<Song>> = songRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = playlistRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    private val _appUpdateState = MutableStateFlow<AppUpdateUiState?>(null)
    val appUpdateState: StateFlow<AppUpdateUiState?> = _appUpdateState.asStateFlow()

    private var launchUpdatePromptHandled = false

    private val playlistSongsFlows = ConcurrentHashMap<Long, StateFlow<List<PlaylistSongWithDetails>>>()

    fun observePlaylistSongs(playlistId: Long): StateFlow<List<PlaylistSongWithDetails>> =
        playlistSongsFlows.getOrPut(playlistId) {
            playlistRepo.observeSongs(playlistId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    fun setPendingImport(pending: PendingImport?) {
        _pendingImport.value = pending
    }

    fun clearPendingImport() {
        _pendingImport.value = null
    }

    suspend fun getSong(id: Long): Song? = songRepo.getById(id)

    suspend fun getPlaylist(id: Long): Playlist? = playlistRepo.getById(id)

    suspend fun getPlaylistSongs(playlistId: Long): List<PlaylistSongWithDetails> =
        playlistRepo.getSongs(playlistId)

    fun deleteSong(id: Long) = viewModelScope.launch { songRepo.delete(id) }

    fun updateSong(id: Long, title: String, keySignature: String, notes: String) =
        viewModelScope.launch {
            val song = songRepo.getById(id) ?: return@launch
            songRepo.update(
                song.copy(
                    title = title.trim().ifBlank { song.title },
                    keySignature = keySignature.trim(),
                    notes = notes.trim(),
                ),
            )
        }

    fun reorderSongs(idsInOrder: List<Long>) = viewModelScope.launch { songRepo.reorder(idsInOrder) }

    fun sortSongsAlpha() = viewModelScope.launch { songRepo.sortAlpha() }

    fun sortSongsByRecentlyAdded() = viewModelScope.launch { songRepo.sortByRecentlyAdded() }

    fun sortSongsByRecentlyViewed() = viewModelScope.launch { songRepo.sortByRecentlyViewed() }

    fun recordSongView(id: Long) = viewModelScope.launch { songRepo.markViewed(id) }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = playlistRepo.create(name)
        withContext(Dispatchers.Main.immediate) { onCreated(id) }
    }

    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { playlistRepo.rename(id, name) }

    fun setPlaylistColor(id: Long, colorArgb: Int?) = viewModelScope.launch {
        playlistRepo.setColor(id, colorArgb)
    }

    fun deletePlaylist(id: Long) = viewModelScope.launch { playlistRepo.delete(id) }

    fun reorderPlaylists(idsInOrder: List<Long>) = viewModelScope.launch {
        playlistRepo.reorder(idsInOrder)
    }

    fun duplicatePlaylist(id: Long, name: String, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val newId = playlistRepo.duplicate(id, name)
        if (newId != null) {
            withContext(Dispatchers.Main.immediate) { onCreated(newId) }
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch {
        playlistRepo.addSong(playlistId, songId)
    }

    fun removeSongFromPlaylist(entryId: Long) = viewModelScope.launch {
        playlistRepo.removeSong(entryId)
    }

    fun reorderPlaylistSongs(playlistId: Long, entryIds: List<Long>) = viewModelScope.launch {
        playlistRepo.reorder(playlistId, entryIds)
    }

    fun saveImport(title: String, keySignature: String, notes: String, onSaved: (Long) -> Unit = {}) =
        viewModelScope.launch {
            val pending = _pendingImport.value ?: return@launch
            val id = ShareImporter.saveSong(songRepo, pending, title, keySignature, notes)
            _pendingImport.value = null
            withContext(Dispatchers.Main.immediate) { onSaved(id) }
        }

    suspend fun matchQuickstartLines(text: String): Pair<List<QuickstartMatcher.MatchResult>, List<Long>> =
        withContext(Dispatchers.IO) {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val archive = songRepo.getAll()
            val results = QuickstartMatcher.matchLines(lines, archive)
            results to QuickstartMatcher.matchedSongIds(results)
        }

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        songRepo.search(query)
    }

    fun applyQuickstart(playlistId: Long, songIds: List<Long>, onDone: () -> Unit = {}) =
        viewModelScope.launch {
            playlistRepo.setSongs(playlistId, songIds)
            withContext(Dispatchers.Main.immediate) { onDone() }
        }

    fun pageCount(song: Song): Int {
        val file = File(song.filePath)
        val type = runCatching { FileType.valueOf(song.fileType) }.getOrDefault(FileType.IMAGE)
        return PdfHelper.pageCount(file, type)
    }

    suspend fun fetchLaunchUpdateVersion(context: android.content.Context): String? {
        if (launchUpdatePromptHandled) return null
        return withContext(Dispatchers.IO) {
            try {
                val local = AppUpdate.installedVersionCode(context)
                val release = AppUpdate.fetchLatestRelease()
                if (release.versionCode > local) release.versionName else null
            } catch (_: Exception) {
                null
            }.also { launchUpdatePromptHandled = true }
        }
    }

    fun clearAppUpdateState() {
        _appUpdateState.value = null
    }

    fun failAppUpdateInstall(message: String) {
        _appUpdateState.value = AppUpdateUiState.Failed(message)
    }

    fun startAppUpdateDownload(context: android.content.Context) {
        val current = _appUpdateState.value
        if (current is AppUpdateUiState.Checking || current is AppUpdateUiState.Downloading) return
        viewModelScope.launch(Dispatchers.IO) {
            _appUpdateState.value = AppUpdateUiState.Checking
            try {
                val local = AppUpdate.installedVersionCode(context)
                val release = AppUpdate.fetchLatestRelease()
                if (release.versionCode <= local) {
                    AppUpdate.clearUpdateCache(context)
                    _appUpdateState.value = AppUpdateUiState.UpToDate(release.versionName)
                    return@launch
                }
                _appUpdateState.value = AppUpdateUiState.Downloading(null)
                val apk = AppUpdate.downloadApk(context, release.downloadUrl) { frac ->
                    _appUpdateState.value = AppUpdateUiState.Downloading(frac)
                }
                _appUpdateState.value = AppUpdateUiState.ReadyToInstall(apk, release.versionName)
            } catch (e: Exception) {
                AppUpdate.clearUpdateCache(context)
                _appUpdateState.value = AppUpdateUiState.Failed(e.message ?: e.toString())
            }
        }
    }
}
