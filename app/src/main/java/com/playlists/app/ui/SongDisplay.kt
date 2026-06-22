package com.playlists.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playlists.app.R
import com.playlists.app.data.Song

object SongDisplay {
    private const val PREVIEW_LEN = 20
    const val PLACEHOLDER_MARKER = " ⚠"

    fun preview(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= PREVIEW_LEN) trimmed else trimmed.take(PREVIEW_LEN) + "…"
    }

    fun keySuffix(keySignature: String): String {
        val key = keySignature.trim()
        return if (key.isNotEmpty()) " ($key)" else ""
    }

    fun titleWithKey(title: String, keySignature: String, isPlaceholder: Boolean = false): String =
        title + keySuffix(keySignature) + if (isPlaceholder) PLACEHOLDER_MARKER else ""

    fun notesLine(notes: String): String = preview(notes)

    fun playlistLine(keySignature: String, notes: String): String {
        val note = preview(notes)
        return note
    }

    fun subtitle(song: Song, pageCount: Int? = null): String {
        val note = preview(song.notes)
        return note
    }

    fun typeBadge(song: Song, pageCount: Int? = null): String =
        if (pageCount != null) "${song.fileType}\n$pageCount pg" else song.fileType
}

@Composable
fun SongTitleWithKey(
    title: String,
    keySignature: String,
    isPlaceholder: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = 1,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = SongDisplay.titleWithKey(title, keySignature),
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isPlaceholder) {
            Icon(
                Icons.Default.Warning,
                contentDescription = stringResource(R.string.placeholder_song),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(18.dp),
            )
        }
    }
}
