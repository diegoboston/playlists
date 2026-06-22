package com.playlists.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer signed APK and hands it to the system package installer.
 *
 * CI publishes a stable asset named [APK_ASSET_NAME] on every main-branch build.
 */
object AppUpdate {

    const val REPO = "diegoboston/playlists"
    const val APK_ASSET_NAME = "app.apk"
    const val UPDATE_APK_FILENAME = "playlists-update.apk"
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/$REPO/releases/latest"
    const val LATEST_APK_URL =
        "https://github.com/$REPO/releases/latest/download/$APK_ASSET_NAME"

    data class ReleaseInfo(
        val versionCode: Long,
        val versionName: String,
        val downloadUrl: String,
    )

    sealed class InstallResult {
        data object Launched : InstallResult()
        data object NeedsPermission : InstallResult()
        data class Failed(val message: String) : InstallResult()
    }

    fun installedVersionCode(context: Context): Long {
        val pi = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        @Suppress("DEPRECATION")
        return pi.versionCode.toLong()
    }

    fun installedVersionName(context: Context): String {
        val pi = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        return pi.versionName ?: installedVersionCode(context).toString()
    }

    fun parseVersionCodeFromTag(tagName: String): Long? {
        val stripped = tagName.trim().removePrefix("v").removePrefix("V")
        return stripped.substringAfterLast('.', missingDelimiterValue = stripped)
            .toLongOrNull()
    }

    fun parseRelease(jsonBody: String): ReleaseInfo? {
        val tagName = extractJsonString(jsonBody, "tag_name") ?: return null
        val versionCode = parseVersionCodeFromTag(tagName) ?: return null
        var downloadUrl: String? = null
        var fallbackUrl: String? = null
        for ((name, url) in extractReleaseAssets(jsonBody)) {
            when {
                name == APK_ASSET_NAME -> downloadUrl = url
                name.endsWith(".apk", ignoreCase = true) && fallbackUrl == null -> fallbackUrl = url
            }
        }
        val resolved = downloadUrl ?: fallbackUrl ?: return null
        return ReleaseInfo(
            versionCode = versionCode,
            versionName = tagName.removePrefix("v").removePrefix("V"),
            downloadUrl = resolved,
        )
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun extractReleaseAssets(json: String): List<Pair<String, String>> {
        val pattern = Regex(
            """"name"\s*:\s*"([^"]+)"[\s\S]*?"browser_download_url"\s*:\s*"([^"]+)""""
        )
        return pattern.findAll(json).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    fun fetchLatestRelease(): ReleaseInfo {
        val conn = openGet(LATEST_RELEASE_API)
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream?.bufferedReader()?.readText()?.take(200)
                error("GitHub API HTTP $code${detail?.let { ": $it" } ?: ""}")
            }
            val body = conn.inputStream.bufferedReader().readText()
            return parseRelease(body)
                ?: error("Release has no $APK_ASSET_NAME asset or unparseable tag")
        } finally {
            conn.disconnect()
        }
    }

    fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Float?) -> Unit,
    ): File {
        val conn = openGet(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("Download failed (HTTP $code)")
            }
            val total = conn.contentLengthLong.takeIf { it > 0L }
            val dest = File(context.cacheDir, UPDATE_APK_FILENAME)
            dest.outputStream().use { out ->
                conn.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        onProgress(
                            if (total != null) (downloaded.toFloat() / total).coerceIn(0f, 1f)
                            else null
                        )
                    }
                }
            }
            return dest
        } finally {
            conn.disconnect()
        }
    }

    fun clearUpdateCache(context: Context) {
        context.cacheDir.listFiles()?.forEach { file ->
            val name = file.name
            if (name == UPDATE_APK_FILENAME ||
                name.startsWith("$UPDATE_APK_FILENAME.") ||
                (name.startsWith("playlists-update") && name.endsWith(".tmp"))
            ) {
                file.delete()
            }
        }
    }

    fun launchInstaller(context: Context, apk: File): InstallResult {
        if (!context.packageManager.canRequestPackageInstalls()) {
            return InstallResult.NeedsPermission
        }
        if (!apk.isFile || apk.length() <= 0L) {
            return InstallResult.Failed("Downloaded APK is missing or empty")
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                InstallResult.Failed("No package installer found")
            } else {
                context.startActivity(intent)
                InstallResult.Launched
            }
        } catch (e: Exception) {
            InstallResult.Failed(e.message ?: e.toString())
        }
    }

    private fun openGet(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "StageManager-Android")
        conn.instanceFollowRedirects = true
        return conn
    }
}
