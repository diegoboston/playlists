package com.playlists.app.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayRemoteControllerTest {
    @Test
    fun shouldClearRunningFlag_whenRunningWithoutSession() {
        assertTrue(PlayRemoteController.shouldClearRunningFlag(running = true, hasSession = false))
    }

    @Test
    fun shouldClearRunningFlag_falseWhenHealthyOrAlreadyStopped() {
        assertFalse(PlayRemoteController.shouldClearRunningFlag(running = false, hasSession = false))
        assertFalse(PlayRemoteController.shouldClearRunningFlag(running = false, hasSession = true))
        assertFalse(PlayRemoteController.shouldClearRunningFlag(running = true, hasSession = true))
    }
}
