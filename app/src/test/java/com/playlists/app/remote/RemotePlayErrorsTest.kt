package com.playlists.app.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlayErrorsTest {
    @Test
    fun format_includesMessageAndCause() {
        val error = IllegalStateException(
            "Cannot run program \"/data/user/0/com.playlists.app/files/cloudflared\": error=13, Permission denied",
            RuntimeException("SELinux denied exec"),
        )
        val formatted = RemotePlayErrors.format(error)
        assertTrue(formatted.contains("Permission denied"))
        assertTrue(formatted.contains("SELinux denied exec"))
        assertTrue(formatted.contains("Caused by"))
    }

    @Test
    fun cloudflaredLibName_isNativeLibrary() {
        assertEquals("libcloudflared.so", CloudflareTunnel.CLOUDFLARED_LIB_NAME)
    }

    @Test
    fun extractPublicTunnelUrl_ignoresApiHost() {
        val line = "POST https://api.trycloudflare.com/tunnel failed: connection reset"
        assertEquals(null, CloudflareTunnel.extractPublicTunnelUrl(line))
    }

    @Test
    fun extractPublicTunnelUrl_picksQuickTunnelHostname() {
        val line = "| https://random-words-here.trycloudflare.com |"
        assertEquals(
            "https://random-words-here.trycloudflare.com",
            CloudflareTunnel.extractPublicTunnelUrl(line),
        )
    }

    @Test
    fun extractPublicTunnelUrl_skipsApiWhenBothPresent() {
        val line =
            "request to https://api.trycloudflare.com/tunnel ok; visit https://abc-def.trycloudflare.com"
        assertEquals(
            "https://abc-def.trycloudflare.com",
            CloudflareTunnel.extractPublicTunnelUrl(line),
        )
    }
}
