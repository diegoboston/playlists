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

    val EXTRACT_CHART_FORMAT = """
        Return a song chart as plain text only — not JSON, not markdown fences.
        Use exactly this layout (header lines, then sections):
        TITLE: song title
        ARTIST: artist name or blank
        KEY: source key if stated on the page, else blank
        CAPO: capo if stated, else blank

        [Verse 1]
        lyric line
        lyric line

        [Chorus]
        lyric line
        Each section label is on its own line in square brackets. Every lyric or chord line is its own line under that label.
        If your reply is cut off, still write complete lines; do not start a JSON object.
    """.trimIndent()

    val EXTRACT_CHART_RULES_SHARED =
        "Ignore navigation, ads, artist biographies, comments, and related-song lists. If words in the lyrics are wrapped in links, still extract them as lyric lines. Prefer the main lyric or chart block on the page."

    val EXTRACT_CHART_RULES_LYRICS_ONLY =
        "For lyrics-only output, do not include any chord symbols, chord-only lines, capo, or key (leave KEY and CAPO blank). Put only lyric text in section lines."

    val EXTRACT_CHART_RULES_CHORDS =
        "Wrap every chord symbol in angle brackets, e.g. <G>, <Am7>, <F/C>. Never put bare chord letters in lyrics. Chord-only lines should contain only bracketed chords and spaces. Keep chords in the original key from the page (do not transpose). Use conventional spelling for the key (e.g. Bb not A# in flat keys)."

    fun extractChartSystem(
        lyricsOnly: Boolean,
        sourceUrl: String,
        songTitle: String,
        artist: String?,
    ): String = """
        ${if (lyricsOnly) EXTRACT_CHART_TASK_LYRICS_ONLY else EXTRACT_CHART_TASK_CHORDS}
        $EXTRACT_CHART_FORMAT
        $EXTRACT_CHART_RULES_SHARED
        ${if (lyricsOnly) EXTRACT_CHART_RULES_LYRICS_ONLY else EXTRACT_CHART_RULES_CHORDS}
        Source URL (do not repeat in the chart): $sourceUrl
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
}
