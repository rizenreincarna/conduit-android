// Port of: Conduit/Services/DashboardSignInRedirect.swift @6ddae94
package app.conduit

import java.net.URI
import java.util.Locale

object DashboardSignInRedirect {
    fun isSignIn(url: URI?, dashboardURL: URI): Boolean {
        val scheme = url?.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (url.swiftHost.isNullOrEmpty()) return false
        if (!ConnectionURLPolicy.originMatches(url, dashboardURL) && !isSecureUpgrade(url, dashboardURL)) return true
        return isSignInPath(url.path.orEmpty())
    }

    private fun isSecureUpgrade(target: URI, dashboard: URI): Boolean {
        if (dashboard.scheme?.lowercase(Locale.ROOT) != "http" || target.scheme?.lowercase(Locale.ROOT) != "https" ||
            target.swiftHost?.lowercase(Locale.ROOT) != dashboard.swiftHost?.lowercase(Locale.ROOT)) return false
        val targetPort = target.portOrNull ?: 443
        return targetPort == 443 || targetPort == (dashboard.portOrNull ?: 80)
    }

    fun redirectIsSignIn(location: String?, requestURL: URI, dashboardURL: URI): Boolean {
        val trimmed = location?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val target = tryOrNull { requestURL.resolve(URI(trimmed)) } ?: return false
        return isSignIn(target, dashboardURL)
    }

    fun isSignInPath(path: String): Boolean {
        val segments = path.lowercase(Locale.ROOT).split('/').filter { it.isNotEmpty() }
        if (segments.any { it in signInSegments }) return true
        return segments.zipWithNext().any { (a, b) -> a == "oauth2" && b == "start" }
    }

    private val signInSegments = setOf("login", "signin", "sign_in", "sign-in")
}
