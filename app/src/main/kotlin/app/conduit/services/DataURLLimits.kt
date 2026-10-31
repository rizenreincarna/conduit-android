// Port of: Conduit/Services/DataURLLimits.swift @6ddae94
package app.conduit

import java.util.Base64
import java.util.Locale

object DataURLLimits {
    const val maxDecodedBytes = 16 * 1024 * 1024
    const val maxBase64Characters = ((maxDecodedBytes + 2) / 3) * 4
    const val maxJSONResponseBytes = 24 * 1024 * 1024

    fun isBase64CharacterCountWithinLimit(count: Int): Boolean = count >= 0 && count <= maxBase64Characters

    fun isBoundedBase64DataURL(value: String, prefix: String? = null): Boolean {
        val comma = value.indexOf(',')
        val header = value.substring(0, if (comma < 0) value.length else comma)
        if (comma < 0 || !header.contains(";base64", ignoreCase = true) ||
            !header.lowercase(Locale.ROOT).startsWith("data:") ||
            !isBase64CharacterCountWithinLimit(value.substring(comma + 1).encodeToByteArray().size)) return false
        if (prefix != null && !value.startsWith(prefix, ignoreCase = true)) return false
        return true
    }

    fun decodeBase64DataURL(value: String, prefix: String? = null): ByteArray? {
        if (!isBoundedBase64DataURL(value, prefix)) return null
        val comma = value.indexOf(',').takeIf { it >= 0 } ?: return null
        val encoded = value.substring(comma + 1)
        val data = tryOrNull { Base64.getDecoder().decode(encoded) } ?: return null
        if (data.isEmpty() || data.size > maxDecodedBytes) return null
        return data
    }
}
