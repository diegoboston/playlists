package com.playlists.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playlists.app.PlaylistsApp
import com.playlists.app.data.FileType
import com.playlists.app.data.Playlist
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.data.Song
import com.playlists.app.render.PlaylistPdfExporter
import com.playlists.app.util.AppUpdate
import com.playlists.app.util.PendingImport
import com.playlists.app.util.PendingChartImport
import com.playlists.app.util.QuickstartMatcher
import com.playlists.app.util.ShareImporter
import com.playlists.app.util.SongStoragePaths
import com.playlists.app.util.SongTitleMigration
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

    private val _pendingChartImport = MutableStateFlow<PendingChartImport?>(null)
    val pendingChartImport: StateFlow<PendingChartImport?> = _pendingChartImport.asStateFlow()

    private val _openPlaylistId = MutableStateFlow<Long?>(null)

    private val _appUpdateState = MutableStateFlow<AppUpdateUiState?>(null)
    val appUpdateState: StateFlow<AppUpdateUiState?> = _appUpdateState.asStateFlow()

    private val _songSortState = MutableStateFlow(SongSortState())
    val songSortState: StateFlow<SongSortState> = _songSortState.asStateFlow()

    private val _songSortGeneration = MutableStateFlow(0)
    val songSortGeneration: StateFlow<Int> = _songSortGeneration.asStateFlow()

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

    fun setOpenPlaylistId(playlistId: Long?) {
        _openPlaylistId.value = playlistId
    }

    fun setPendingChartImport(url: String, titleHint: String) {
        _pendingChartImport.value = PendingChartImport(
            url = url,
            titleHint = titleHint,
            playlistId = _openPlaylistId.value,
        )
    }

    fun consumePendingChartImport(): PendingChartImport? {
        val pending = _pendingChartImport.value
        _pendingChartImport.value = null
        return pending
    }

    suspend fun getSong(id: Long): Song? = songRepo.getById(id)

    suspend fun getPlaylist(id: Long): Playlist? = playlistRepo.getById(id)

    suspend fun getPlaylistSongs(playlistId: Long): List<PlaylistSongWithDetails> =
        playlistRepo.getSongs(playlistId)

    suspend fun exportPlaylistPdf(playlistId: Long): PlaylistPdfExporter.Result? =
        withContext(Dispatchers.IO) {
            val playlist = playlistRepo.getById(playlistId) ?: return@withContext null
            val entries = playlistRepo.getSongs(playlistId)
            if (entries.isEmpty()) return@withContext null
            PlaylistPdfExporter.export(
                cacheDir = getApplication<Application>().cacheDir,
                playlistName = playlist.name,
                entries = entries,
            )
        }

    suspend fun prepareSongDelete(id: Long): SongDeletePrompt? = withContext(Dispatchers.IO) {
        val song = songRepo.getById(id) ?: return@withContext null
        val playlistNames = playlistRepo.playlistNamesForSong(id)
        SongDeletePrompt(song = song, playlistNames = playlistNames)
    }

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

    fun sortSongs(criterion: SongSortCriterion) = viewModelScope.launch {
        val current = _songSortState.value
        val reversed = if (current.criterion == criterion) !current.reversed else false
        _songSortState.value = SongSortState(criterion, reversed)
        applySongSort(criterion, reversed)
        _songSortGeneration.value++
    }

    suspend fun refreshSongListSort(): Boolean {
        val before = songRepo.getAll().map { it.id }
        val state = _songSortState.value
        applySongSort(state.criterion, state.reversed)
        val after = songRepo.getAll().map { it.id }
        return before != after
    }

    private suspend fun applySongSort(criterion: SongSortCriterion, reversed: Boolean) {
        when (criterion) {
            SongSortCriterion.Alpha -> songRepo.sortAlpha(reversed)
            SongSortCriterion.Added -> songRepo.sortByRecentlyAdded(reversed)
            SongSortCriterion.Viewed -> songRepo.sortByRecentlyViewed(reversed)
        }
    }

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

    fun addPlaceholderToPlaylist(
        playlistId: Long,
        title: String,
        onAdded: () -> Unit = {},
    ) = viewModelScope.launch {
        val parsed = com.playlists.app.util.SongTitleMigration.parse(title)
        val songId = songRepo.createPlaceholder(
            title = parsed.title,
            keySignature = parsed.keySignature,
        )
        playlistRepo.addSong(playlistId, songId)
        withContext(Dispatchers.Main.immediate) { onAdded() }
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

    fun applyQuickstartWithPlaceholders(
        playlistId: Long,
        results: List<QuickstartMatcher.MatchResult>,
        onDone: () -> Unit = {},
    ) = viewModelScope.launch {
        val ids = results.map { result ->
            result.song?.id ?: run {
                val parsed = SongTitleMigration.parse(result.line)
                songRepo.createPlaceholder(
                    title = parsed.title,
                    keySignature = parsed.keySignature,
                )
            }
        }
        playlistRepo.setSongs(playlistId, ids)
        withContext(Dispatchers.Main.immediate) { onDone() }
    }

    fun pageCount(song: Song): Int {
        val file = SongStoragePaths.resolve(song.filePath)
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
