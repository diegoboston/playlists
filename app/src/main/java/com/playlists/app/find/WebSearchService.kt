package com.playlists.app.find

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object PageFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetchText(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "StageManager/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body?.string().orEmpty()
            return htmlToText(html).take(40_000)
        }
    }

    internal fun htmlToText(html: String): String {
        var text = html
            .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
            .replace(Regex("(?is)<(br|p|div|li|h[1-6]|tr)[^>]*>"), "\n")
            .replace(Regex("(?is)<[^>]+>"), " ")
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return text.replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}

object WebSearchService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val RESULT_LINK = Regex(
        """class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
        RegexOption.IGNORE_CASE,
    )
    private val SNIPPET = Regex(
        """class="result__snippet"[^>]*>(.*?)</(?:a|td|div)>""",
        RegexOption.IGNORE_CASE,
    )

    fun search(query: String, maxResults: Int = 5): List<SearchResult> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            .header("User-Agent", "StageManager/1.0")
            .post(
                okhttp3.FormBody.Builder()
                    .add("q", query)
                    .build(),
            )
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val html = response.body?.string().orEmpty()
            return parseResults(html, maxResults)
        }
    }

    internal fun parseResults(html: String, maxResults: Int): List<SearchResult> {
        val links = RESULT_LINK.findAll(html).map { match ->
            val href = decodeRedirect(match.groupValues[1])
            val title = stripTags(match.groupValues[2]).trim()
            href to title
        }.filter { (url, title) ->
            url.startsWith("http") && title.isNotEmpty()
        }.toList()

        val snippets = SNIPPET.findAll(html).map { stripTags(it.groupValues[1]).trim() }.toList()
        return links.take(maxResults).mapIndexed { index, (url, title) ->
            SearchResult(
                title = title,
                url = url,
                snippet = snippets.getOrElse(index) { "" },
            )
        }
    }

    private fun decodeRedirect(href: String): String {
        val decoded = href.replace("&amp;", "&")
        if (decoded.contains("uddg=")) {
            val param = decoded.substringAfter("uddg=", decoded)
                .substringBefore("&")
            return java.net.URLDecoder.decode(param, Charsets.UTF_8.name())
        }
        return if (decoded.startsWith("//")) "https:$decoded" else decoded
    }

    private fun stripTags(html: String): String =
        html.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").trim()
}
