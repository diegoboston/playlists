package com.playlists.app.ai

/**
 * All OpenAI prompt text (chart assistant + camera import).
 * Add new prompt strings here; callers import from this object.
 */
object AiPrompts {
    fun parseIntentSystem(playlistsContextJson: String): String = """
        You parse voice commands for a chord-chart assistant app.
        Return JSON only with fields:
        action (always "find_chart" for now),
        songTitle (required),
        artist (optional),
        playlistName (optional).
        Do not ask for or require a musical key — transposition happens in the preview UI.
        Ignore whether the user asked for chords, lyrics, or both — the app adds that to the search query itself.
        Context: $playlistsContextJson
    """.trimIndent()

    val EXTRACT_CHART_TASK_LYRICS_ONLY =
        "Extract a lyrics-only song document from the web page text. Preserve section labels such as Verse, Chorus, Bridge, and Intro."

    val EXTRACT_CHART_TASK_CHORDS =
        "Extract a chord chart with lyrics from the web page text."

    val EXTRACT_CHART_RULES_LYRICS_ONLY =
        "For lyrics-only output, do not include any chord symbols, chord-only lines, capo, sourceKey, or key. Put only lyric text in lines and keep meaningful section labels."

    val EXTRACT_CHART_RULES_CHORDS =
        "Wrap every chord symbol in angle brackets, e.g. <G>, <Am7>, <F/C>. Never put bare chord letters in lyrics. Chord-only lines should contain only bracketed chords and spaces. Keep chords in the original key from the page (do not transpose)."

    fun extractChartSystem(
        lyricsOnly: Boolean,
        sourceUrl: String,
        songTitle: String,
        artist: String?,
    ): String = """
        ${if (lyricsOnly) EXTRACT_CHART_TASK_LYRICS_ONLY else EXTRACT_CHART_TASK_CHORDS}
        Return JSON only:
        {
          "title": "...",
          "artist": "...",
          "sourceKey": "key on page if stated",
          "key": "same as sourceKey",
          "capo": null or string,
          "columns": 1,
          "sections": [{"label":"Verse 1","lines":["<G>  <C>  <G>","When I <Am> find myself in times of trouble"]}],
          "notes": "optional",
          "sourceUrl": "$sourceUrl"
        }
        ${if (lyricsOnly) EXTRACT_CHART_RULES_LYRICS_ONLY else EXTRACT_CHART_RULES_CHORDS}
        Use conventional spelling for the key (e.g. Bb not A# in flat keys).
        Song requested: $songTitle ${artist.orEmpty()}
    """.trimIndent()

    val EXTRACT_TITLE_FROM_IMAGE_SYSTEM = """
        You read a photo of sheet music, a chord chart, a lyric sheet, or a set list.
        Return JSON only: {"title":"..."}.
        Put the most prominent printed song title in title.
        Do not include key, artist, page numbers, or filenames unless that text is the title.
        If no title is readable, return {"title":""}.
    """.trimIndent()

    const val EXTRACT_TITLE_FROM_IMAGE_USER = "What is the song title on this page?"

    val FLATTEN_IMAGE = """
        This is a photo of sheet music, a chord chart, or lyrics on paper.
        Produce a clean flattened top-down document scan: correct perspective and skew,
        even lighting, high contrast black ink on white paper, crop to the page,
        remove table, hands, and background.
        Preserve every note, lyric, chord symbol, and title exactly — do not invent or omit content.
    """.trimIndent()
}
