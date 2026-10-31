// Port of: Conduit/Services/ConnectionURLPolicy.swift @6ddae94
package app.conduit

import java.net.URI
import java.util.Locale

sealed class ConnectionURLPolicyError : Exception(), LocalizedError {
    data object invalidURL : ConnectionURLPolicyError()
    data object insecureTransport : ConnectionURLPolicyError()

    override val errorDescription: String
        get() = when (this) {
            invalidURL -> AppLocalization.string("Enter a valid dashboard URL.")
            insecureTransport -> AppLocalization.string("Remote dashboards must use HTTPS; HTTP is allowed only for local networks (localhost, private LAN, and Tailscale).")
        }
}

object ConnectionURLPolicy {
    fun isAllowedTransport(url: URI?): Boolean {
        val scheme = url?.scheme?.lowercase(Locale.ROOT) ?: return false
        val host = url.swiftHost?.takeIf { it.isNotEmpty() } ?: return false
        if (url.rawUserInfo != null) return false
        return scheme == "https" || (scheme == "http" && isInsecureTransportAllowed(host))
    }

    // Turnstile's about documents are allowed only in subframes, never main-frame navigation.
    fun isTurnstileRequiredSubframeDocument(url: URI?): Boolean {
        var raw = url?.absoluteString?.lowercase(Locale.ROOT) ?: return false
        if (!raw.startsWith("about:")) return false
        raw = raw.removePrefix("about:").trimEnd('/')
        return raw == "blank" || raw == "srcdoc"
    }

    fun isAllowedWebViewSubframeTransport(url: URI?): Boolean =
        isAllowedTransport(url) || isTurnstileRequiredSubframeDocument(url)

    fun normalizedBaseURL(value: String): String {
        val trimmed = value.trim()
        val components = urlFromString(trimmed) ?: throw ConnectionURLPolicyError.invalidURL
        val scheme = components.scheme?.lowercase(Locale.ROOT) ?: throw ConnectionURLPolicyError.invalidURL
        val host = components.swiftHost?.trim()?.takeIf { it.isNotEmpty() } ?: throw ConnectionURLPolicyError.invalidURL
        if (components.rawUserInfo != null || components.rawQuery != null || components.rawFragment != null) {
            throw ConnectionURLPolicyError.invalidURL
        }
        if (scheme != "https" && scheme != "http") throw ConnectionURLPolicyError.invalidURL
        if (scheme == "http" && !isInsecureTransportAllowed(host)) throw ConnectionURLPolicyError.insecureTransport
        var normalized = trimmed
        while (normalized.length > scheme.length + 3 && normalized.endsWith('/')) normalized = normalized.dropLast(1)
        return normalized
    }

    fun webSocketURL(baseURL: String, path: String, queryItems: List<URLQueryItem> = emptyList()): URI {
        val normalized = normalizedBaseURL(baseURL)
        val components = urlFromString(normalized) ?: throw ConnectionURLPolicyError.invalidURL
        val baseScheme = components.scheme.lowercase(Locale.ROOT)
        val basePath = components.path.orEmpty().trim('/')
        val routePath = path.trim('/')
        val joinedPath = listOf(basePath, routePath).filter { it.isNotEmpty() }.joinToString("/")
        val url = URI(if (baseScheme == "https") "wss" else "ws", null, components.swiftHost, components.port, "/$joinedPath", null, null)
        return url.appending(queryItems)
    }

    fun originMatches(url: URI?, expected: URI?): Boolean {
        val scheme = url?.scheme?.lowercase(Locale.ROOT) ?: return false
        val host = url.swiftHost?.lowercase(Locale.ROOT) ?: return false
        val expectedScheme = expected?.scheme?.lowercase(Locale.ROOT) ?: return false
        val expectedHost = expected.swiftHost?.lowercase(Locale.ROOT) ?: return false
        return originMatches(scheme, host, url.portOrNull, expectedScheme, expectedHost, expected.portOrNull)
    }

    fun originMatches(scheme: String, host: String, port: Int, expected: URI?): Boolean {
        val expectedScheme = expected?.scheme?.lowercase(Locale.ROOT) ?: return false
        val expectedHost = expected.swiftHost?.lowercase(Locale.ROOT) ?: return false
        return originMatches(scheme, host, port, expectedScheme, expectedHost, expected.portOrNull)
    }

    private fun originMatches(scheme: String, host: String, port: Int?, expectedScheme: String, expectedHost: String, expectedPort: Int?): Boolean {
        if (scheme.lowercase(Locale.ROOT) != expectedScheme || host.lowercase(Locale.ROOT) != expectedHost) return false
        return effectivePort(scheme, port) == effectivePort(expectedScheme, expectedPort)
    }

    private fun effectivePort(scheme: String, port: Int?): Int? {
        // WKSecurityOrigin reports 0 for the default scheme port.
        if (port != null && port > 0) return port
        return when (scheme.lowercase(Locale.ROOT)) {
            "http", "ws" -> 80
            "https", "wss" -> 443
            else -> null
        }
    }

    private fun isInsecureTransportAllowed(value: String): Boolean {
        val host = value.lowercase(Locale.ROOT).trim('[', ']')
        return isLoopbackHost(host) || isPrivateLANHost(host) || isTailscaleHost(host)
    }

    private fun isLoopbackHost(value: String): Boolean {
        val host = value.lowercase(Locale.ROOT).trim('[', ']')
        return host == "localhost" || host == "127.0.0.1" || host == "::1"
    }

    private fun isTailscaleHost(value: String): Boolean {
        val host = value.lowercase(Locale.ROOT)
        if (host.endsWith(".ts.net")) return true
        val octets = ipv4Octets(host) ?: return false
        return octets[0] == 100 && octets[1] in 64..127
    }

    private fun isPrivateLANHost(value: String): Boolean {
        val octets = ipv4Octets(value) ?: return false
        return when (octets[0]) {
            10 -> true
            172 -> octets[1] in 16..31
            192 -> octets[1] == 168
            else -> false
        }
    }

    private fun ipv4Octets(value: String): List<Int>? {
        val components = value.split('.')
        if (components.size != 4 || !components.all { component ->
                component.isNotEmpty() && component.all { it in '0'..'9' } && (component.length == 1 || component.first() != '0')
            }) return null
        val octets = components.mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || !octets.all { it in 0..255 }) return null
        return octets
    }
}
