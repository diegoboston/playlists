package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateTest {

    @Test
    fun parseVersionCodeFromTag_acceptsVPrefixAndVersionName() {
        assertEquals(123L, AppUpdate.parseVersionCodeFromTag("v1.0.123"))
        assertEquals(42L, AppUpdate.parseVersionCodeFromTag("1.0.42"))
        assertEquals(7L, AppUpdate.parseVersionCodeFromTag("v7"))
    }

    @Test
    fun parseVersionCodeFromTag_rejectsGarbage() {
        assertNull(AppUpdate.parseVersionCodeFromTag("v1.0.beta"))
        assertNull(AppUpdate.parseVersionCodeFromTag(""))
    }

    @Test
    fun parseRelease_findsAbiSpecificAsset() {
        val json = """
            {
              "tag_name": "v1.0.99",
              "assets": [
                {
                  "name": "app-1.0.99-arm64-v8a.apk",
                  "browser_download_url": "https://example.com/versioned-arm64.apk"
                },
                {
                  "name": "app-arm64-v8a.apk",
                  "browser_download_url": "https://example.com/app-arm64-v8a.apk"
                },
                {
                  "name": "app-armeabi-v7a.apk",
                  "browser_download_url": "https://example.com/app-armeabi-v7a.apk"
                }
              ]
            }
        """.trimIndent()

        val release = AppUpdate.parseRelease(json, abi = "arm64-v8a")
        assertNotNull(release)
        assertEquals(99L, release!!.versionCode)
        assertEquals("https://example.com/app-arm64-v8a.apk", release.downloadUrl)

        val release32 = AppUpdate.parseRelease(json, abi = "armeabi-v7a")
        assertNotNull(release32)
        assertEquals("https://example.com/app-armeabi-v7a.apk", release32!!.downloadUrl)
    }

    @Test
    fun parseRelease_fallsBackToAnyApkAsset() {
        val json = """
            {
              "tag_name": "v1.0.5",
              "assets": [
                {
                  "name": "app-1.0.5-armeabi-v7a.apk",
                  "browser_download_url": "https://example.com/fallback.apk"
                }
              ]
            }
        """.trimIndent()

        val release = AppUpdate.parseRelease(json, abi = "armeabi-v7a")
        assertNotNull(release)
        assertEquals(5L, release!!.versionCode)
        assertEquals("https://example.com/fallback.apk", release.downloadUrl)
    }

    @Test
    fun apkAssetName_mapsKnownAbis() {
        assertEquals("app-arm64-v8a.apk", AppUpdate.apkAssetName("arm64-v8a"))
        assertEquals("app-armeabi-v7a.apk", AppUpdate.apkAssetName("armeabi-v7a"))
    }
}
