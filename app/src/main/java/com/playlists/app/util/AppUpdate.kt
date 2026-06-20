package com.playlists.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer signed APK matching this device's CPU ABI.
 *
 * CI publishes stable per-ABI assets:
 * - [APK_ASSET_ARM64] for 64-bit ARM phones
 * - [APK_ASSET_ARM32] for 32-bit ARM phones (incl. most Android 4.3 devices)
 */
object AppUpdate {

    const val REPO = "diegoboston/playlists"
    const val APK_ASSET_ARM64 = "app-arm64-v8a.apk"
    const val APK_ASSET_ARM32 = "app-armeabi-v7a.apk"
    const val UPDATE_APK_FILENAME = "playlists-update.apk"
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/$REPO/releases/latest"

    data class ReleaseInfo(
        val versionCode: Long,
        val versionName: String,
        val downloadUrl: String,
        val abi: String,
    )

    sealed class InstallResult {
        data object Launched : InstallResult()
        data object NeedsPermission : InstallResult()
    }

    fun apkAssetName(abi: String): String = when (abi) {
        "arm64-v8a" -> APK_ASSET_ARM64
        "armeabi-v7a" -> APK_ASSET_ARM32
        else -> "app-$abi.apk"
    }

    fun latestApkUrl(abi: String): String =
        "https://github.com/$REPO/releases/latest/download/${apkAssetName(abi)}"

    /** Primary CPU ABI used to pick the correct release APK. */
    fun deviceAbi(): String {
        val abis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.toList()
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(Build.CPU_ABI, Build.CPU_ABI2)
        }
        return when {
            abis.contains("arm64-v8a") -> "arm64-v8a"
            abis.contains("armeabi-v7a") -> "armeabi-v7a"
            abis.contains("x86_64") -> "x86_64"
            abis.contains("x86") -> "x86"
            else -> "armeabi-v7a"
        }
    }

    fun installedVersionCode(context: Context): Long {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }
    }

    fun parseVersionCodeFromTag(tagName: String): Long? {
        val stripped = tagName.trim().removePrefix("v").removePrefix("V")
        return stripped.substringAfterLast('.', missingDelimiterValue = stripped)
            .toLongOrNull()
    }

    fun parseRelease(jsonBody: String, abi: String = deviceAbi()): ReleaseInfo? {
        val tagName = extractJsonString(jsonBody, "tag_name") ?: return null
        val versionCode = parseVersionCodeFromTag(tagName) ?: return null
        val preferredName = apkAssetName(abi)
        var downloadUrl: String? = null
        var fallbackUrl: String? = null
        for ((name, url) in extractReleaseAssets(jsonBody)) {
            when {
                name == preferredName -> downloadUrl = url
                name.endsWith(".apk", ignoreCase = true) && fallbackUrl == null -> fallbackUrl = url
            }
        }
        val resolved = downloadUrl ?: fallbackUrl ?: return null
        return ReleaseInfo(
            versionCode = versionCode,
            versionName = tagName.removePrefix("v").removePrefix("V"),
            downloadUrl = resolved,
            abi = abi,
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

    fun fetchLatestRelease(abi: String = deviceAbi()): ReleaseInfo {
        val conn = openGet(LATEST_RELEASE_API)
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream?.bufferedReader()?.readText()?.take(200)
                error("GitHub API HTTP $code${detail?.let { ": $it" } ?: ""}")
            }
            val body = conn.inputStream.bufferedReader().readText()
            return parseRelease(body, abi)
                ?: error("Release has no ${apkAssetName(abi)} asset or unparseable tag")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return InstallResult.NeedsPermission
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return InstallResult.Launched
    }

    private fun openGet(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Playlists-Android")
        conn.instanceFollowRedirects = true
        return conn
    }
}
