// Port of: Shared/LogRedaction.swift @6ddae94
package app.conduit

object LogRedaction {
    fun describing(error: Throwable?): String = error?.let { redactingURLs(it.toString()) } ?: "nil"
    fun message(error: Throwable): String = redactingURLs(error.localizedDescription)
    fun redactingURLs(text: String): String = redactingHosts(urlPattern.replace(text, "<url>"))
    fun redactingHosts(text: String): String {
        var result = text
        for (match in hostPattern.findAll(text).toList().asReversed()) {
            val token = match.value
            val name = token.takeWhile { it != '/' && it != '?' }
            if (!name.contains(':') && name.substringAfterLast('.').lowercase(java.util.Locale.ROOT) in fileExtensions) continue
            result = result.replaceRange(match.range, "<host>")
        }
        return result
    }
    private val urlPattern = Regex("""[A-Za-z][A-Za-z0-9+.\-]*://[^\s"'<>{}]*[^\s"'<>{},;.)\]]""")
    private val hostPattern = Regex("""(?<=pretending to be “)[^”\s]+(?=”)|(?<![\w.\-:/])(?:\[[0-9A-Fa-f.%]*:[0-9A-Za-z:.%]*\](?::\d{1,5})?|(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?|(?:[A-Za-z0-9_](?:[A-Za-z0-9_\-]*[A-Za-z0-9_])?\.)+[A-Za-z](?:[A-Za-z0-9\-]*[A-Za-z0-9])?:\d{1,5}|(?:[a-z0-9_](?:[a-z0-9_\-]*[a-z0-9_])?\.)+[a-z]{2,63})(?![\w\-]|\.[A-Za-z0-9])(?:[/?][^\s"'<>{}]*[^\s"'<>{},;.)\]])?""")
    private val fileExtensions = setOf("caf", "csv", "gif", "heic", "html", "jpeg", "jpg", "json", "log", "pdf", "plist", "png", "sqlite", "swift", "tar", "toml", "txt", "wav", "xcconfig", "xml", "yaml", "yml")
}
