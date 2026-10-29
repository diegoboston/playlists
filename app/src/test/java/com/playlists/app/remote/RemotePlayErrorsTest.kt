package com.playlists.app.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlayErrorsTest {
    @Test
    fun assignedUrlAloneDoesNotMeanTunnelIsConnected() {
        assertEquals(false, CloudflareTunnel.isStartupReady("https://abc.trycloudflare.com", false))
        assertEquals(false, CloudflareTunnel.isStartupReady(null, true))
        assertEquals(true, CloudflareTunnel.isStartupReady("https://abc.trycloudflare.com", true))
    }

    @Test
    fun connectionRegistrationRequiresTheActualCloudflaredEvent() {
        assertEquals(false, CloudflareTunnel.isRegisteredConnection("Your quick Tunnel has been created!"))
        assertEquals(false, CloudflareTunnel.isRegisteredConnection("Retrying connection in up to 2s"))
        assertEquals(true, CloudflareTunnel.isRegisteredConnection("INF Registered tunnel connection connIndex=0 protocol=http2"))
    }

    @Test
    fun publicCloudflareErrorExplainsConnectorFailureRatherThanDnsPropagation() {
        val warning = RemotePlayErrors.tunnelReachabilityWarning("HTTP 530 (after 2 attempts)")
        assertTrue(warning.contains("healthy connector"))
        assertTrue(warning.contains("newly shown session URL"))
        assertEquals(false, warning.contains("30–60 seconds"))
    }

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

    @Test
    fun looksLikeDnsFailure_detectsPort53Refused() {
        val msg = """failed to request quick Tunnel: lookup api.trycloudflare.com on [::1]:53: read udp [::1]:39143->[::1]:53: read: connection refused"""
        assertTrue(RemotePlayErrors.looksLikeDnsFailure(msg))
    }

    @Test
    fun tunnelReachabilityWarning_dnsPropagationHint() {
        val detail =
            """Unable to resolve host "abc.trycloudflare.com": No address associated with hostname"""
        val warning = RemotePlayErrors.tunnelReachabilityWarning(detail)
        assertTrue(RemotePlayErrors.looksLikeTunnelHostnameUnresolved(detail))
        assertTrue(warning.contains("30–60 seconds"))
        assertTrue(warning.contains("airplane mode"))
    }
    @Test
    fun transportFailuresExplainWhichNetworkPathFailed() {
        val quic = "ERR Failed to dial a quic connection: timeout: no recent network activity"
        val tcp = "ERR dial tcp 198.41.200.13:7844: i/o timeout"
        assertTrue(RemotePlayErrors.format(TunnelConnectionException(quic)).contains("Retry using TCP"))
        assertTrue(RemotePlayErrors.transportFailureHint(tcp)!!.contains("TCP port 7844"))
        assertTrue(RemotePlayErrors.transportFailureHint("$quic\n$tcp")!!.contains("Both UDP and TCP"))
    }

    @Test
    fun apiTimeoutAndDnsFailureAreNotClassifiedAsBlockedTunnelTcp() {
        assertEquals(null, RemotePlayErrors.transportFailureHint("dial tcp 1.2.3.4:443: i/o timeout"))
        assertEquals(null, RemotePlayErrors.transportFailureHint("lookup api.trycloudflare.com: DNS timeout"))
        assertEquals(null, RemotePlayErrors.transportFailureHint("protocol=http2 connected\nAPI request timeout"))
    }

    @Test
    fun badGatewayExplainsOriginFailure() {
        val warning = RemotePlayErrors.tunnelReachabilityWarning("HTTP 502 (after 2 attempts)")
        assertTrue(warning.contains("local web server"))
        assertTrue(warning.contains("local server result"))
        assertEquals(false, warning.contains("Wait a few seconds"))
    }

    @Test
    fun transportIsTakenFromRegistrationRatherThanAttemptOrFallback() {
        assertEquals(null, CloudflareTunnel.connectedProtocol("Initial protocol quic"))
        assertEquals(null, CloudflareTunnel.connectedProtocol("Switching to fallback protocol http2"))
        assertEquals("http2", CloudflareTunnel.connectedProtocol(
            "Registered tunnel connection protocol=quic\nRegistered tunnel connection protocol=http2",
        ))
        assertEquals(null, CloudflareTunnel.connectedProtocol("Registered tunnel connection protocol=http2other"))
    }

    @Test
    fun tcpRetryUsesHttp2AndKeepsTheLocalOrigin() {
        val binary = java.io.File("/native/libcloudflared.so")
        val command = CloudflareTunnel.command(binary, 52341, TunnelTransport.TCP)
        assertEquals("http2", command[command.indexOf("--protocol") + 1])
        assertEquals("http://127.0.0.1:52341", command[command.indexOf("--url") + 1])
        val auto = CloudflareTunnel.command(binary, 52341, TunnelTransport.AUTO)
        assertEquals("auto", auto[auto.indexOf("--protocol") + 1])
    }
}
