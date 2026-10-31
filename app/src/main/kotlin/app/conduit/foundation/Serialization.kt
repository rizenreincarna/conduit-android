package app.conduit

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.serializer
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.UUID
import kotlin.math.floor

val ConduitJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = false
}

class JSONDecoder {
    inline fun <reified T> decode(data: ByteArray): T = ConduitJson.decodeFromString(data.utf8String()
        ?: throw SerializationException("Invalid UTF-8"))
}

class JSONEncoder {
    var outputFormatting: Set<OutputFormatting> = emptySet()
    enum class OutputFormatting { sortedKeys }
    inline fun <reified T> encode(value: T): ByteArray {
        val element = ConduitJson.encodeToJsonElement(serializer<T>(), value)
        return ConduitJson.encodeToString(JsonElement.serializer(), if (OutputFormatting.sortedKeys in outputFormatting) sorted(element) else element).encodeToByteArray()
    }
    fun sorted(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.toSortedMap().mapValues { sorted(it.value) })
        is JsonArray -> JsonArray(element.map(::sorted))
        else -> element
    }
}

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.uuidString)
    override fun deserialize(decoder: Decoder): UUID = uuidFromString(decoder.decodeString()) ?: throw SerializationException("Invalid UUID")
}

object URISerializer : KSerializer<URI> {
    override val descriptor = PrimitiveSerialDescriptor("URI", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: URI) = encoder.encodeString(value.absoluteString)
    override fun deserialize(decoder: Decoder): URI = urlFromString(decoder.decodeString()) ?: throw SerializationException("Invalid URL")
}

object Base64ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("Data", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ByteArray) = encoder.encodeString(Base64.getEncoder().encodeToString(value))
    override fun deserialize(decoder: Decoder): ByteArray = try {
        Base64.getDecoder().decode(decoder.decodeString())
    } catch (error: IllegalArgumentException) { throw SerializationException("Invalid base64", error) }
}

// Swift's synthesized Date coding uses seconds since 2001-01-01, not Unix time.
object ReferenceDateInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("ReferenceDate", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeDouble(value.timeIntervalSince1970 - 978307200.0)
    override fun deserialize(decoder: Decoder): Instant = instantFromEpochSeconds(decoder.decodeDouble() + 978307200.0)
}

object EpochSecondsInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("EpochSeconds", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeDouble(value.timeIntervalSince1970)
    override fun deserialize(decoder: Decoder): Instant = instantFromEpochSeconds(decoder.decodeDouble())
}

object Iso8601InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("ISO8601", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = try { Instant.parse(decoder.decodeString()) }
        catch (error: DateTimeParseException) { throw SerializationException("Invalid ISO-8601 date", error) }
}

val Instant.timeIntervalSince1970: Double get() = epochSecond.toDouble() + nano / 1_000_000_000.0
fun Instant.timeIntervalSince(other: Instant): Double = timeIntervalSince1970 - other.timeIntervalSince1970
val Instant.timeIntervalSinceNow: Double get() = timeIntervalSince(Instant.now())
fun Instant.addingTimeInterval(seconds: Double): Instant {
    require(seconds.isFinite())
    val whole = floor(seconds).toLong()
    return plusSeconds(whole).plusNanos(((seconds - whole) * 1_000_000_000).toLong())
}
fun instantFromEpochSeconds(value: Double): Instant {
    require(value.isFinite())
    val whole = floor(value).toLong()
    return Instant.ofEpochSecond(whole, ((value - whole) * 1_000_000_000).toLong())
}
val distantPast: Instant = Instant.parse("0001-01-01T00:00:00Z")
val distantFuture: Instant = Instant.parse("4001-01-01T00:00:00Z")

object JSONSerialization {
    fun jsonObject(data: ByteArray): Any? = fromElement(ConduitJson.parseToJsonElement(data.utf8String()
        ?: throw SerializationException("Invalid UTF-8")))

    fun data(value: Any?): ByteArray = ConduitJson.encodeToString(JsonElement.serializer(), toElement(value)).encodeToByteArray()

    private fun fromElement(value: JsonElement): Any? = when (value) {
        JsonNull -> null
        is JsonObject -> value.mapValues { fromElement(it.value) }
        is JsonArray -> value.map(::fromElement)
        is JsonPrimitive -> if (value.isString) value.content else value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull
            ?: throw SerializationException("Invalid JSON primitive")
    }

    private fun toElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value).also { require(value.toDouble().isFinite()) { "Non-finite JSON number" } }
        is List<*> -> JsonArray(value.map(::toElement))
        is Map<*, *> -> JsonObject(value.entries.associate { (key, item) ->
            require(key is String) { "JSON object keys must be strings" }; key to toElement(item)
        })
        else -> throw SerializationException("Unsupported JSON value: ${value::class}")
    }
}
