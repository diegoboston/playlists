package com.playlists.app.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemotePlayUrlsTest {

    @Test
    fun playlistSuffix_includesPlaylistQueryWhenPresent() {
        assertEquals("/?playlist=3", RemotePlayUrls.playlistSuffix(3L))
        assertEquals("/", RemotePlayUrls.playlistSuffix(null))
    }
}
