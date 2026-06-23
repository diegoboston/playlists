package com.playlists.app.util

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object FileStorage {
    fun songsDir(): File = StageManagerStorage.songsDir().also { it.mkdirs() }

    fun storeStream(input: InputStream, extension: String): File {
        val file = File(songsDir(), "${UUID.randomUUID()}.$extension")
        file.outputStream().use { out -> input.copyTo(out) }
        return file
    }

    fun storeBytes(bytes: ByteArray, extension: String): File {
        val file = File(songsDir(), "${UUID.randomUUID()}.$extension")
        file.writeBytes(bytes)
        return file
    }

    fun extensionForMime(mimeType: String?): String = when {
        mimeType == null -> "bin"
        mimeType.contains("pdf") -> "pdf"
        mimeType.contains("png") -> "png"
        mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
        mimeType.contains("gif") -> "gif"
        mimeType.contains("webp") -> "webp"
        else -> mimeType.substringAfterLast('/').ifBlank { "bin" }
    }

    fun downloadUrl(urlString: String): Pair<ByteArray, String>? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val mime = connection.contentType?.substringBefore(';')?.trim()
            val bytes = connection.inputStream.use { it.readBytes() }
            bytes to (mime ?: guessMimeFromUrl(urlString))
        } finally {
            connection.disconnect()
        }
    }

    private fun guessMimeFromUrl(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
