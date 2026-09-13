package com.playlists.app.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.playlists.app.R
import com.playlists.app.data.Song
import com.playlists.app.ui.PdfHelper
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.util.FileStamp
import com.playlists.app.util.SongAnnotate
import com.playlists.app.util.SongStoragePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AnnotateHost(
    songId: Long?,
    viewModel: PlaylistsViewModel,
    onFinished: (Song?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var prepared by remember { mutableStateOf<Song?>(null) }
    var startedForId by remember { mutableStateOf<Long?>(null) }
    var showXodoPrompt by remember { mutableStateOf(false) }
    var beforeStamp by remember { mutableStateOf<FileStamp?>(null) }
    var destSong by remember { mutableStateOf<Song?>(null) }

    fun finishSession(song: Song?, before: FileStamp?) {
        destSong = null
        beforeStamp = null
        if (song == null || before == null) {
            onFinished(null)
            return
        }
        scope.launch {
            val dest = SongStoragePaths.resolve(song.filePath)
            val changed = withContext(Dispatchers.IO) {
                val did = SongAnnotate.stampChanged(dest, before)
                if (did) PdfHelper.invalidate(dest)
                did
            }
            if (!changed) {
                Toast.makeText(context, R.string.annotate_unchanged, Toast.LENGTH_SHORT).show()
            }
            onFinished(viewModel.getSong(song.id))
        }
    }

    DisposableEffect(destSong, beforeStamp, lifecycleOwner) {
        val song = destSong
        val before = beforeStamp
        if (song == null || before == null) {
            return@DisposableEffect onDispose { }
        }
        var sawPause = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> sawPause = true
                Lifecycle.Event.ON_RESUME -> {
                    if (sawPause) finishSession(song, before)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun launchEditor(targetPackage: String?) {
        val song = prepared ?: return
        val source = SongStoragePaths.resolve(song.filePath)
        val uri = SongAnnotate.songUri(context, source)
        if (uri == null) {
            Toast.makeText(context, R.string.annotate_failed, Toast.LENGTH_SHORT).show()
            onFinished(null)
            return
        }
        destSong = song
        beforeStamp = SongAnnotate.stampOf(source)
        runCatching {
            context.startActivity(SongAnnotate.editorIntent(context, uri, targetPackage))
        }.onFailure {
            destSong = null
            beforeStamp = null
            Toast.makeText(context, R.string.annotate_no_editor, Toast.LENGTH_SHORT).show()
            onFinished(null)
        }
    }

    LaunchedEffect(songId) {
        val id = songId
        if (id == null) {
            startedForId = null
            return@LaunchedEffect
        }
        if (startedForId == id) return@LaunchedEffect
        startedForId = id
        prepared = null
        showXodoPrompt = false
        val song = viewModel.ensurePdfForAnnotate(id)
        if (song == null) {
            Toast.makeText(context, R.string.annotate_failed, Toast.LENGTH_SHORT).show()
            onFinished(null)
            return@LaunchedEffect
        }
        prepared = song
        when {
            SongAnnotate.isXodoInstalled(context) -> launchEditor(SongAnnotate.XODO_PACKAGE)
            else -> showXodoPrompt = true
        }
    }

    if (showXodoPrompt) {
        AlertDialog(
            onDismissRequest = {
                showXodoPrompt = false
                onFinished(null)
            },
            title = { Text(stringResource(R.string.annotate_install_xodo_title)) },
            text = { Text(stringResource(R.string.annotate_install_xodo_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showXodoPrompt = false
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, SongAnnotate.playStoreUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        onFinished(null)
                    },
                ) {
                    Text(stringResource(R.string.annotate_install_xodo))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showXodoPrompt = false
                        if (!SongAnnotate.hasPdfEditor(context)) {
                            Toast.makeText(context, R.string.annotate_no_editor, Toast.LENGTH_SHORT).show()
                            onFinished(null)
                        } else {
                            launchEditor(null)
                        }
                    },
                ) {
                    Text(stringResource(R.string.annotate_use_another_app))
                }
            },
        )
    }
}
