// Port of: Conduit/Services/DashboardPath.swift @6ddae94
package app.conduit

object DashboardPath {
    fun encodedQueryComponent(value: String): String? = app.conduit.encodedQueryComponent(value)

    const val conduitProfileParameter = "conduit_profile"
    const val dashboardProfilesCapability = "dashboard-profiles"

    fun socketProfileItems(profile: String, parameter: String): List<URLQueryItem> {
        if (parameter == "profile") return listOf(URLQueryItem(parameter, profile))
        if (profile.isEmpty() || profile == "default") return emptyList()
        return listOf(URLQueryItem(parameter, profile))
    }

    fun withProfile(path: String, profile: String, parameter: String = "profile"): String {
        if (profile == "default") return path
        return withExplicitProfile(path, profile, parameter)
    }

    fun bodyWithProfile(body: Map<String, Any?>, profile: String): Map<String, Any?> {
        if (profile == "default" || profile.isEmpty()) return body
        return body + ("profile" to profile)
    }

    fun withExplicitProfile(path: String, profile: String, parameter: String = "profile"): String {
        if (profile.isEmpty()) return path
        val encoded = encodedQueryComponent(profile) ?: return path
        return "$path${if (path.contains('?')) "&" else "?"}$parameter=$encoded"
    }
}
