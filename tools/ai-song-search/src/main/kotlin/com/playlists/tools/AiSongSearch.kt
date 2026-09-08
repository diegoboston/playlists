package com.playlists.tools

import com.playlists.app.ai.ChartAssistantException
import com.playlists.app.ai.ChartAssistantService
import com.playlists.app.ai.ChartDraft
import com.playlists.app.ai.ChartIntent
import com.playlists.app.ai.ChartSearchMode
import com.playlists.app.ai.OpenAiClient
import com.playlists.app.ai.OpenAiException
import com.playlists.app.find.WebSearchService
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * Local CLI that follows the in-app typed AI song-search path:
 * [ChartIntent.fromTypedQuery] → DuckDuckGo → parse the first result →
 * confirm Yes / Try next / Cancel.
 */
fun main(args: Array<String>) {
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8))
    System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, Charsets.UTF_8))
    try {
        run(args)
    } catch (e: UsageException) {
        System.err.println(e.message)
        exitProcess(2)
    } catch (e: Exception) {
        System.err.println(e.userMessage())
        exitProcess(1)
    }
}

private fun run(args: Array<String>) {
    val options = parseArgs(args)
    if (options.help) {
        printHelp()
        return
    }

    val interactive = System.console() != null
    val queryText = options.query?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (interactive) readLinePrompt("Song (SONG TITLE by ARTIST)") else null
    if (queryText.isNullOrBlank()) {
        throw UsageException("Enter a song title (same as typing in the app).")
    }

    val searchMode = options.mode ?: if (interactive) {
        readModePrompt()
    } else {
        ChartSearchMode.ChordsAndLyrics
    }

    val intent = ChartIntent.fromTypedQuery(queryText, searchMode)
        ?: throw ChartAssistantException("Enter a song title")

    println("Heard: ${intent.editableQuery()}")
    println("Mode: ${searchMode.label()}")
    println("Web search query: ${intent.searchQuery()}")
    println()
    println("Working… searching the web")

    val apiKey = System.getenv("OPENAI_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw UsageException(
            "Set OPENAI_API_KEY to parse a result (the app stores it in Settings; this CLI reads the env var).",
        )
    val service = ChartAssistantService(OpenAiClient(apiKey))
    var results = WebSearchService.search(intent.searchQuery())
    if (results.isEmpty()) {
        throw ChartAssistantException("No search results")
    }

    while (results.isNotEmpty()) {
        val chosen = results.first()
        results = results.drop(1)
        println()
        println("Working… fetching and parsing (${results.size} more after this):")
        println("  ${chosen.title}")
        println("  ${chosen.url}")
        val draft = try {
            service.fetchAndExtractChart(chosen, intent)
        } catch (e: UsageException) {
            throw e
        } catch (e: Exception) {
            println()
            println(e.userMessage())
            if (results.isEmpty()) {
                throw ChartAssistantException(e.userMessage())
            }
            println("Trying next result.")
            continue
        }
        println()
        printDraft(draft, searchMode)
        println()
        println("Is this correct?")
        when (confirmChoice(interactive = interactive, autoYes = options.yes)) {
            ConfirmChoice.Yes -> {
                println("Yes — accepted (CLI does not save to the archive).")
                return
            }
            ConfirmChoice.Next -> {
                if (results.isEmpty()) {
                    throw ChartAssistantException("No more search results")
                }
                println("Trying next result.")
            }
            ConfirmChoice.Cancel -> throw UsageException("Cancelled")
        }
    }
}

private data class Options(
    val help: Boolean = false,
    val mode: ChartSearchMode? = null,
    val yes: Boolean = false,
    val query: String? = null,
)

private class UsageException(message: String) : Exception(message)

private fun parseArgs(args: Array<String>): Options {
    var help = false
    var mode: ChartSearchMode? = null
    var yes = false
    val queryParts = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "-h", "--help" -> help = true
            "--lyrics" -> mode = ChartSearchMode.LyricsOnly
            "--chords" -> mode = ChartSearchMode.ChordsAndLyrics
            "--yes" -> yes = true
            "--mode" -> {
                val value = args.getOrNull(i + 1)
                    ?: throw UsageException("Missing value for --mode (lyrics|chords)")
                mode = parseMode(value)
                i++
            }
            "--" -> {
                queryParts.addAll(args.drop(i + 1))
                break
            }
            else -> {
                if (arg.startsWith("-")) {
                    throw UsageException("Unknown option: $arg\n\n${helpText()}")
                }
                queryParts.add(arg)
            }
        }
        i++
    }
    return Options(
        help = help,
        mode = mode,
        yes = yes,
        query = queryParts.joinToString(" ").trim().ifEmpty { null },
    )
}

private fun parseMode(value: String): ChartSearchMode = when (value.lowercase()) {
    "lyrics", "lyrics-only", "lyrics_only" -> ChartSearchMode.LyricsOnly
    "chords", "chords-lyrics", "chords+lyrics" -> ChartSearchMode.ChordsAndLyrics
    else -> throw UsageException("Unknown mode '$value' (use lyrics or chords)")
}

private fun ChartSearchMode.label(): String = when (this) {
    ChartSearchMode.ChordsAndLyrics -> "Chords + lyrics"
    ChartSearchMode.LyricsOnly -> "Lyrics only"
}

private enum class ConfirmChoice { Yes, Next, Cancel }

private fun confirmChoice(interactive: Boolean, autoYes: Boolean): ConfirmChoice {
    if (autoYes || !interactive) {
        println("Yes")
        return ConfirmChoice.Yes
    }
    while (true) {
        when (readLinePrompt("Yes / Try next / Cancel").lowercase()) {
            "y", "yes" -> return ConfirmChoice.Yes
            "n", "next", "try next", "t" -> return ConfirmChoice.Next
            "c", "cancel", "q", "quit" -> return ConfirmChoice.Cancel
            else -> println("Enter yes, next, or cancel.")
        }
    }
}

private fun printDraft(draft: ChartDraft, searchMode: ChartSearchMode) {
    val lyricsOnly = searchMode == ChartSearchMode.LyricsOnly
    println("Parsed chart")
    println("────────────")
    println("Title: ${draft.title}")
    draft.artist?.let { println("Artist: $it") }
    if (lyricsOnly) {
        println("Mode: Lyrics only (chords, capo, and key stripped like the app)")
    } else {
        draft.displayKeyLabel()?.let { key ->
            val guessed = if (draft.isChartKeyGuessed()) " (guessed from first chord)" else ""
            println("Key: $key$guessed")
        }
        draft.capo?.let { println("Capo: $it") }
    }
    draft.sourceUrl?.let { println("Source: $it") }
    draft.notes?.let { println("Notes: $it") }
    println()
    draft.sections.forEach { section ->
        if (section.label.isNotBlank()) {
            println("[${section.label}]")
        }
        section.lines.forEach { println(it) }
        println()
    }
}

private fun readModePrompt(): ChartSearchMode {
    println("Search mode:")
    println("  1) Chords + lyrics")
    println("  2) Lyrics only")
    while (true) {
        when (readLinePrompt("Choice [1/2]").lowercase()) {
            "1", "c", "chords" -> return ChartSearchMode.ChordsAndLyrics
            "2", "l", "lyrics" -> return ChartSearchMode.LyricsOnly
            else -> println("Enter 1 or 2.")
        }
    }
}

private fun readLinePrompt(label: String): String {
    print("$label: ")
    System.out.flush()
    return readlnOrNull()?.trim().orEmpty()
}

private fun printHelp() {
    println(helpText())
}

private fun helpText(): String = """
    Simulate in-app AI song search locally (typed query + lyrics/chords).

    Usage:
      bash scripts/ai-song-search.sh [--lyrics|--chords] [--yes] [query...]

    Options:
      --lyrics            Lyrics only (app toggle)
      --chords            Chords + lyrics (app default)
      --mode lyrics|chords
      --yes               Accept the first chart that parses (no prompt)
      -h, --help          Show this help

    Parses the first DuckDuckGo hit, then asks Is this correct?
    (yes / try next / cancel), matching the app. Failed pages are skipped.
    Requires OPENAI_API_KEY. Query is the same as the app text field:
    "SONG TITLE by ARTIST" (artist optional).

    Examples:
      export OPENAI_API_KEY=sk-...
      bash scripts/ai-song-search.sh
      bash scripts/ai-song-search.sh --lyrics Volare
      bash scripts/ai-song-search.sh --lyrics --yes "X Colpa di chi"
""".trimIndent()

private fun Throwable.userMessage(): String = when (this) {
    is UsageException -> message ?: "Usage error"
    is OpenAiException -> message ?: "OpenAI error"
    is ChartAssistantException -> message ?: "Chart assistant error"
    else -> message ?: "Something went wrong"
}
