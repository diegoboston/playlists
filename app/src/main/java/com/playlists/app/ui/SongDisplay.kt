package com.playlists.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.playlists.app.data.Song

object SongDisplay {
    private const val PREVIEW_LEN = 20
    const val PLACEHOLDER_MARKER = " 🚧"

    fun preview(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= PREVIEW_LEN) trimmed else trimmed.take(PREVIEW_LEN) + "…"
    }

    fun keySuffix(keySignature: String): String {
        val key = keySignature.trim()
        return if (key.isNotEmpty()) " ($key)" else ""
    }

    fun adjustedSongTitle(title: String, keySignature: String): String =
        title + keySuffix(keySignature)

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
fun NotesText(
    notes: String,
    style: TextStyle,
    color: Color,
    maxLines: Int = 1,
) {
    val uriHandler = LocalUriHandler.current
    val urlRegex = Regex("""https?://\S+""")
    val annotated = AnnotatedString.Builder().apply {
        var end = 0
        urlRegex.findAll(notes).forEach { match ->
            append(notes.substring(end, match.range.first))
            pushStringAnnotation("URL", match.value.trimEnd('.', ',', ')'))
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                append(match.value)
            }
            pop()
            end = match.range.last + 1
        }
        append(notes.substring(end))
    }.toAnnotatedString()
    ClickableText(
        text = annotated,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onClick = { offset ->
            annotated
                .getStringAnnotations("URL", offset, offset)
                .firstOrNull()
                ?.let { uriHandler.openUri(it.item) }
        },
    )
}

@Composable
fun SongTitleWithKey(
    title: String,
    keySignature: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = 1,
) {
    Text(
        text = SongDisplay.adjustedSongTitle(title, keySignature),
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
