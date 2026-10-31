// Port of: ConduitTests/ConnectionURLPolicyOriginTests.swift @6ddae94
package app.conduit

import org.junit.Test
import kotlin.test.*

class ConnectionURLPolicyOriginTests {

    // MARK: - originMatches (URL, URL)

    @Test fun testSameOriginMatches() {
        val a = urlFromString("https://example.com")
        val b = urlFromString("https://example.com")
        assertTrue(ConnectionURLPolicy.originMatches(a, expected = b))
    }

    @Test fun testDifferentHostDoesNotMatch() {
        val a = urlFromString("https://attacker.com")
        val b = urlFromString("https://example.com")
        assertFalse(ConnectionURLPolicy.originMatches(a, expected = b))
    }

    @Test fun testDifferentSchemeDoesNotMatch() {
        val a = urlFromString("http://example.com")
        val b = urlFromString("https://example.com")
        assertFalse(ConnectionURLPolicy.originMatches(a, expected = b))
    }

    @Test fun testCaseInsensitiveHost() {
        val a = urlFromString("https://EXAMPLE.COM")
        val b = urlFromString("https://example.com")
        assertTrue(ConnectionURLPolicy.originMatches(a, expected = b))
    }

    @Test fun testCaseInsensitiveScheme() {
        val a = urlFromString("HTTPS://example.com")
        val b = urlFromString("https://example.com")
        assertTrue(ConnectionURLPolicy.originMatches(a, expected = b))
    }

    // MARK: - Port normalization (default ports)

    @Test fun testDefaultHTTPPort80MatchesNoPort() {
        // https on 443 should match https with no explicit port
        val a = urlFromString("https://example.com:443")
        val b = urlFromString("https://example.com")
        assertTrue(ConnectionURLPolicy.originMatches(a, expected = b))
    }

    @Test fun testDefaultHTTPPort80MatchesNoPortHTTP() {
        val a = urlFromString("http://example.com:80")
        val b = urlFromString("http://example.com")
        assertTrue(ConnectionURLPolicy.originMatches(a, expected = b))
    }

    @Test fun testDifferentPortDoesNotMatch() {
        val a = urlFromString("https://example.com:8443")
        val b = urlFromString("https://example.com")
        assertFalse(ConnectionURLPolicy.originMatches(a, expected = b))
    }

    @Test fun testExplicitSamePortMatches() {
        val a = urlFromString("https://example.com:9119")
        val b = urlFromString("https://example.com:9119")
        assertTrue(ConnectionURLPolicy.originMatches(a, expected = b))
    }

    // MARK: - Port 0 (WKSecurityOrigin edge case)

    @Test fun testPort0MatchesDefaultHTTPS() {
        // WKSecurityOrigin reports port 0 for default-port connections.
        // This simulates the fix for the Tailscale Serve issue.
        val result = ConnectionURLPolicy.originMatches(
            scheme = "https",
            host = "example.ts.net",
            port = 0,
            expected = urlFromString("https://example.ts.net")
        )
        assertTrue(result)
    }

    @Test fun testPort0MatchesDefaultHTTP() {
        val result = ConnectionURLPolicy.originMatches(
            scheme = "http",
            host = "localhost",
            port = 0,
            expected = urlFromString("http://localhost")
        )
        assertTrue(result)
    }

    @Test fun testPort0DoesNotMatchNonDefaultPort() {
        val result = ConnectionURLPolicy.originMatches(
            scheme = "https",
            host = "example.com",
            port = 0,
            expected = urlFromString("https://example.com:9119")
        )
        assertFalse(result)
    }

    @Test fun testPort0DoesNotMatchDifferentScheme() {
        val result = ConnectionURLPolicy.originMatches(
            scheme = "wss",
            host = "example.com",
            port = 0,
            expected = urlFromString("https://example.com")
        )
        // wss and https both default to 443, so ports match.
        // But schemes differ, so origin should not match.
        assertFalse(result)
    }

    // MARK: - Nil inputs

    @Test fun testNilURLReturnsFalse() {
        assertFalse(ConnectionURLPolicy.originMatches(null, expected = urlFromString("https://example.com")))
        assertFalse(ConnectionURLPolicy.originMatches(urlFromString("https://example.com"), expected = null))
    }

    @Test fun testNilExpectedReturnsFalse() {
        val result = ConnectionURLPolicy.originMatches(
            scheme = "https",
            host = "example.com",
            port = 443,
            expected = null
        )
        assertFalse(result)
    }

    // MARK: - URL with embedded credentials (should be rejected upstream)

    @Test fun testNormalizedBaseURLRejectsUserInfo() {
        // The normalizer should reject URLs with embedded credentials
        assertFails { ConnectionURLPolicy.normalizedBaseURL("https://user:pass@example.com") }
    }

    // MARK: - normalizedBaseURL trailing slash

    @Test fun testNormalizedBaseURLStripsTrailingSlashes() {
        val normalized = ConnectionURLPolicy.normalizedBaseURL("https://example.com///")
        assertEquals(normalized, "https://example.com")
    }

    @Test fun testNormalizedBaseURLPreservesPort() {
        val normalized = ConnectionURLPolicy.normalizedBaseURL("https://example.com:9119")
        assertEquals(normalized, "https://example.com:9119")
    }

    // MARK: - webSocketURL scheme translation

    @Test fun testWebSocketURLHTTPToWS() {
        val wsURL = ConnectionURLPolicy.webSocketURL(
            baseURL = "http://localhost:9119",
            path = "/api/ws"
        )
        assertEquals(wsURL.scheme, "ws")
        assertTrue(wsURL.absoluteString.contains("/api/ws"))
    }

    @Test fun testWebSocketURLHTTPSToWSS() {
        val wsURL = ConnectionURLPolicy.webSocketURL(
            baseURL = "https://example.com",
            path = "/api/ws"
        )
        assertEquals(wsURL.scheme, "wss")
    }

    @Test fun testWebSocketURLPreservesGatewayPath() {
        val wsURL = ConnectionURLPolicy.webSocketURL(
            baseURL = "https://example.com/gateway",
            path = "/api/ws"
        )
        assertTrue(wsURL.absoluteString.contains("/gateway/api/ws"))
    }

    @Test fun testWebSocketURLWithQueryItems() {
        val wsURL = ConnectionURLPolicy.webSocketURL(
            baseURL = "https://example.com",
            path = "/api/ws",
            queryItems = listOf(URLQueryItem(name = "ticket", value = "abc123"))
        )
        assertEquals(wsURL.query, "ticket=abc123")
    }
}
