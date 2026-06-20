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
    fun parseRelease_findsStableApkAsset() {
        val json = """
            {
              "tag_name": "v1.0.99",
              "assets": [
                {
                  "name": "app-1.0.99.apk",
                  "browser_download_url": "https://example.com/versioned.apk"
                },
                {
                  "name": "app.apk",
                  "browser_download_url": "https://example.com/app.apk"
                }
              ]
            }
        """.trimIndent()

        val release = AppUpdate.parseRelease(json)
        assertNotNull(release)
        assertEquals(99L, release!!.versionCode)
        assertEquals("1.0.99", release.versionName)
        assertEquals("https://example.com/app.apk", release.downloadUrl)
    }

    @Test
    fun parseRelease_fallsBackToAnyApkAsset() {
        val json = """
            {
              "tag_name": "v1.0.5",
              "assets": [
                {
                  "name": "app-1.0.5.apk",
                  "browser_download_url": "https://example.com/fallback.apk"
                }
              ]
            }
        """.trimIndent()

        val release = AppUpdate.parseRelease(json)
        assertNotNull(release)
        assertEquals(5L, release!!.versionCode)
        assertEquals("https://example.com/fallback.apk", release.downloadUrl)
    }
}
