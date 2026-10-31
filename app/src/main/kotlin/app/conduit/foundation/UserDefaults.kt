package app.conduit

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Base64

// android: Snapshot-backed SharedPreferences; scratch instances never touch Android.
class UserDefaults private constructor(private val preferences: SharedPreferences?) {
    private var values by mutableStateOf<Map<String, Any>>(preferences?.all?.mapNotNull { (key, value) ->
        value?.let { key to it }
    }?.toMap() ?: emptyMap())

    @Synchronized fun string(forKey: String): String? = values[forKey] as? String
    @Synchronized fun bool(forKey: String): Boolean = values[forKey] as? Boolean ?: false
    @Synchronized fun integer(forKey: String): Int = (values[forKey] as? Number)?.toInt() ?: 0
    @Synchronized fun double(forKey: String): Double = when (val value = values[forKey]) {
        is Number -> value.toDouble()
        is String -> value.removePrefix("double:").toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
    @Synchronized fun data(forKey: String): ByteArray? = string(forKey)?.takeIf { it.startsWith("data:") }?.let {
        tryOrNull { Base64.getDecoder().decode(it.removePrefix("data:")) }
    }
    @Synchronized fun objectForKey(forKey: String): Any? = values[forKey]

    @Synchronized fun set(value: Any?, forKey: String) {
        if (value == null) { removeObject(forKey); return }
        val stored: Any = when (value) {
            is ByteArray -> "data:" + Base64.getEncoder().encodeToString(value)
            is Double -> "double:$value"
            is String, is Boolean, is Int, is Long, is Float -> value
            else -> error("Unsupported UserDefaults value: ${value::class}")
        }
        values = values + (forKey to stored)
        preferences?.edit()?.also { editor ->
            when (stored) {
                is String -> editor.putString(forKey, stored)
                is Boolean -> editor.putBoolean(forKey, stored)
                is Int -> editor.putInt(forKey, stored)
                is Long -> editor.putLong(forKey, stored)
                is Float -> editor.putFloat(forKey, stored)
            }
        }?.apply()
    }

    @Synchronized fun removeObject(forKey: String) {
        values = values - forKey
        preferences?.edit()?.remove(forKey)?.apply()
    }

    companion object {
        var standard: UserDefaults = UserDefaults(null)
            internal set
        internal fun install(preferences: SharedPreferences) { standard = UserDefaults(preferences) }
        fun scratch(): UserDefaults = UserDefaults(null)
    }
}
