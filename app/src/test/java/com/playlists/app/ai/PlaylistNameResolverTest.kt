package com.playlists.app.ai

import com.playlists.app.data.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaylistNameResolverTest {
    private val playlists = listOf(
        Playlist(id = 1, name = "Sunday set"),
        Playlist(id = 2, name = "Rehearsal"),
    )

    @Test
    fun resolve_exactName() {
        val match = PlaylistNameResolver.resolve("Sunday set", playlists, defaultPlaylistId = 2)
        assertEquals(1L, match?.id)
    }

    @Test
    fun resolve_defaultsWhenBlank() {
        val match = PlaylistNameResolver.resolve(null, playlists, defaultPlaylistId = 2)
        assertEquals(2L, match?.id)
    }

    @Test
    fun resolve_fuzzyMatch() {
        val match = PlaylistNameResolver.resolve("sunday", playlists, defaultPlaylistId = 2)
        assertNotNull(match)
        assertEquals(1L, match?.id)
    }
}
