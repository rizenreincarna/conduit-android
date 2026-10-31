package app.conduit

import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.UUID

fun urlFromString(value: String): URI? = try {
    URI(value)
} catch (_: URISyntaxException) { null }

val URI.absoluteString: String get() = toString()
val URI.swiftHost: String? get() = host?.removePrefix("[")?.removeSuffix("]")
val URI.portOrNull: Int? get() = port.takeIf { it >= 0 }
val URI.lastPathComponent: String get() = path.orEmpty().trimEnd('/').substringAfterLast('/')

data class URLQueryItem(val name: String, val value: String?)

// android: Foundation query items use RFC 3986 escaping, never form encoding (+ for spaces).
internal fun encodedQueryComponent(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt() and 0xff
        if (c in 65..90 || c in 97..122 || c in 48..57 || c.toChar() in "-._~") append(c.toChar())
        else append("%%%02X".format(Locale.ROOT, c))
    }
}

internal fun encodedQueryItems(items: List<URLQueryItem>): String = items.joinToString("&") {
    encodedQueryComponent(it.name) + (it.value?.let { value -> "=" + encodedQueryComponent(value) } ?: "")
}

fun URI.appending(queryItems: List<URLQueryItem>): URI {
    if (queryItems.isEmpty()) return this
    val query = listOfNotNull(rawQuery, encodedQueryItems(queryItems)).joinToString("&")
    val base = toString().substringBefore('#').substringBefore('?')
    return URI(base + "?" + query + (rawFragment?.let { "#$it" } ?: ""))
}

fun URI.appendingPathComponent(component: String): URI {
    val pathURL = URI(scheme, userInfo, swiftHost, port, path.orEmpty().trimEnd('/') + "/" + component.trimStart('/'), null, null)
    return URI(pathURL.toString() + (rawQuery?.let { "?$it" } ?: "") + (rawFragment?.let { "#$it" } ?: ""))
}

val UUID.uuidString: String get() = toString().uppercase(Locale.ROOT)
fun uuidFromString(value: String): UUID? = if (value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
    UUID.fromString(value)
} else null

fun ByteArray.utf8String(): String? = try {
    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(this)).toString()
} catch (_: java.nio.charset.CharacterCodingException) { null }
