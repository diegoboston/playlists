package com.playlists.app.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlayUrlsTest {

    @Test
    fun playlistSuffix_includesPlaylistQueryWhenPresent() {
        assertEquals("/?playlist=3", RemotePlayUrls.playlistSuffix(3L))
        assertEquals("/", RemotePlayUrls.playlistSuffix(null))
    }

    @Test
    fun shouldShowStableUrl_requiresActiveRegistrationInStableMode() {
        assertTrue(
            RemotePlayUrls.shouldShowStableUrl(
                mode = RemotePlayMode.STABLE,
                stableConfigured = true,
                stableUrlActive = true,
            ),
        )
        assertTrue(
            !RemotePlayUrls.shouldShowStableUrl(
                mode = RemotePlayMode.STABLE,
                stableConfigured = true,
                stableUrlActive = false,
            ),
        )
    }

    @Test
    fun shouldShowStableUrl_showsWhenConfiguredForCloudflareMode() {
        assertTrue(
            RemotePlayUrls.shouldShowStableUrl(
                mode = RemotePlayMode.CLOUDFLARE,
                stableConfigured = true,
                stableUrlActive = false,
            ),
        )
    }
}
