package com.playlists.app.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelRedirectClientTest {

    @Test
    fun normalizeTunnelBaseUrl_acceptsQuickTunnelHost() {
        assertEquals(
            "https://abc-def.trycloudflare.com",
            TunnelRedirectClient.normalizeTunnelBaseUrl("https://abc-def.trycloudflare.com/"),
        )
    }

    @Test
    fun normalizeTunnelBaseUrl_rejectsApiHost() {
        assertNull(
            TunnelRedirectClient.normalizeTunnelBaseUrl("https://api.trycloudflare.com/tunnel"),
        )
    }

    @Test
    fun normalizeTunnelBaseUrl_rejectsNonQuickTunnel() {
        assertNull(TunnelRedirectClient.normalizeTunnelBaseUrl("https://example.com"))
    }

    @Test
    fun buildWorkerBaseUrl_usesPlayWorkerName() {
        assertEquals(
            "https://play.myaccount.workers.dev",
            TunnelRedirectClient.buildWorkerBaseUrl("myaccount"),
        )
    }
}
