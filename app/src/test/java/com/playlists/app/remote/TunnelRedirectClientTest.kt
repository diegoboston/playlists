package com.playlists.app.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    @Test
    fun interpretValidateWriteSecretResponse_acceptsSuccess() {
        assertTrue(
            TunnelRedirectClient.interpretValidateWriteSecretResponse(200, """{"ok":true}""").isSuccess,
        )
    }

    @Test
    fun interpretValidateWriteSecretResponse_rejects200WithoutOkBody() {
        assertTrue(TunnelRedirectClient.interpretValidateWriteSecretResponse(200, "").isFailure)
        assertTrue(
            TunnelRedirectClient.interpretValidateWriteSecretResponse(200, "No tunnel active").isFailure,
        )
    }

    @Test
    fun interpretValidateWriteSecretResponse_rejectsUnauthorized() {
        assertTrue(TunnelRedirectClient.interpretValidateWriteSecretResponse(401).isFailure)
    }

    @Test
    fun interpretValidateWriteSecretResponse_rejectsMissingWorker() {
        assertTrue(TunnelRedirectClient.interpretValidateWriteSecretResponse(404).isFailure)
    }

    @Test
    fun pushPlaylistPdf_rejectsInvalidPin() {
        val file = File.createTempFile("playlist-export", ".pdf").apply {
            writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46))
            deleteOnExit()
        }
        assertTrue(
            TunnelRedirectClient.pushPlaylistPdf(
                workerBaseUrl = "https://play.test.workers.dev",
                secret = "secret",
                pin = "abc",
                playlistId = 1L,
                playlistName = "Gig",
                pdfFile = file,
            ).isFailure,
        )
    }
}
