package app.conduit

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.net.URI
import java.time.Instant
import java.util.Locale
import kotlin.test.*

class TransportBoundaryTest {
    @Test fun publicCleartextAndDisguisedPrivateHostsAreRejected() {
        for (host in listOf("example.com", "8.8.8.8", "010.0.0.1", "100.64.attacker.example", "192.168.1.256", "172.32.0.1", "100.128.0.1", "10.1", "0x0a000001")) {
            assertFalse(ConnectionURLPolicy.isAllowedTransport(urlFromString("http://$host")), host)
            assertFailsWith<ConnectionURLPolicyError> { ConnectionURLPolicy.normalizedBaseURL("http://$host") }
        }
    }
    @Test fun privateHostsAndSecureRemoteHostsAreAllowed() {
        for (address in listOf("http://localhost", "http://127.0.0.1", "http://[::1]", "http://10.0.2.2:9119", "http://172.16.0.1", "http://192.168.1.2", "http://100.64.0.1", "http://100.127.255.255", "http://demo.ts.net", "https://example.com")) {
            assertTrue(ConnectionURLPolicy.isAllowedTransport(urlFromString(address)), address)
            assertEquals(address, ConnectionURLPolicy.normalizedBaseURL(address))
        }
    }
    @Test fun credentialsQueriesFragmentsAndMalformedURLsAreRejected() {
        for (address in listOf("", "example.com", "https://", "https://user@example.com", "https://user:pass@example.com", "https://example.com?token=x", "https://example.com#fragment", "file:///private", "https://example.com:bad")) {
            assertFailsWith<ConnectionURLPolicyError.invalidURL>(address) { ConnectionURLPolicy.normalizedBaseURL(address) }
        }
    }
    @Test fun aboutDocumentsStayInsideSubframeBoundary() {
        for (document in listOf("about:blank", "about:srcdoc", "ABOUT:BLANK/")) {
            assertTrue(ConnectionURLPolicy.isAllowedWebViewSubframeTransport(URI(document)))
            assertFalse(ConnectionURLPolicy.isAllowedTransport(URI(document)))
        }
        for (document in listOf("about:config", "about:blank?x", "data:text/html,hi", "javascript:alert(1)")) {
            assertFalse(ConnectionURLPolicy.isAllowedWebViewSubframeTransport(URI(document)))
        }
    }
    @Test fun webSocketPathAndQueryAreEncodedOnce() {
        val url = ConnectionURLPolicy.webSocketURL("https://example.com/hermes%20gateway", "/api/ws", listOf(URLQueryItem("ticket", "a+/=&?雪")))
        assertEquals("/hermes gateway/api/ws", url.path)
        assertEquals("ticket=a%2B%2F%3D%26%3F%E9%9B%AA", url.rawQuery)
        assertFalse(url.rawPath.contains("%2520"))
    }
    @Test fun ipv6AndDefaultPortsMatchWithoutDNS() {
        assertEquals("::1", URI("https://[::1]").swiftHost)
        assertTrue(ConnectionURLPolicy.originMatches(URI("https://[::1]:443/x"), URI("https://[::1]")))
        assertEquals("wss://[::1]/api/ws", ConnectionURLPolicy.webSocketURL("https://[::1]", "/api/ws").toString())
    }
    @Test fun urlPolicyDoesNotDependOnTurkishDeviceLocale() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(ConnectionURLPolicy.originMatches(URI("HTTPS://IDENTITY.EXAMPLE"), URI("https://identity.example")))
        } finally { Locale.setDefault(old) }
    }
}

class UrlShimTest {
    @Test fun uuidParsingIsStrictAndEncodingIsUppercase() {
        assertNull(uuidFromString("1-1-1-1-1"))
        assertEquals("12345678-ABCD-1234-ABCD-123456789ABC", uuidFromString("12345678-abcd-1234-abcd-123456789abc")?.uuidString)
    }
    @Test fun invalidUtf8DoesNotBecomeReplacementCharacters() {
        assertNull(byteArrayOf(0xc3.toByte(), 0x28).utf8String())
        assertEquals("雪", "雪".encodeToByteArray().utf8String())
    }
    @Test fun queryAppendingKeepsExistingEncodedQueryAndFragment() {
        assertEquals("https://h/a?old=a%2Bb&new=a%20b#section", URI("https://h/a?old=a%2Bb#section").appending(listOf(URLQueryItem("new", "a b"))).toString())
    }
}

class SerializationShimTest {
    @Serializable data class Payload(val count: Int, val optional: String? = null)
    @Test fun unknownFieldsAreAcceptedAndWrongFieldTypesAreRejected() {
        assertEquals(Payload(3), JSONDecoder().decode<Payload>("""{"count":3,"future":true}""".encodeToByteArray()))
        assertFailsWith<SerializationException> { JSONDecoder().decode<Payload>("""{"count":"3"}""".encodeToByteArray()) }
        assertFailsWith<SerializationException> { JSONDecoder().decode<Payload>("""{"count":3.5}""".encodeToByteArray()) }
    }
    @Test fun referenceDateMatchesSwiftAndEpochDatesStaySeparate() {
        val date = Instant.parse("2001-01-01T00:00:00Z")
        assertEquals("0.0", ConduitJson.encodeToString(ReferenceDateInstantSerializer, date))
        assertEquals(date, ConduitJson.decodeFromString(ReferenceDateInstantSerializer, "0"))
        assertEquals("978307200.0", ConduitJson.encodeToString(EpochSecondsInstantSerializer, date))
        assertEquals(Instant.parse("1969-12-31T23:59:59.750Z"), instantFromEpochSeconds(-0.25))
    }
    @Test fun sortedKeysSortNestedObjects() {
        val encoder = JSONEncoder().apply { outputFormatting = setOf(JSONEncoder.OutputFormatting.sortedKeys) }
        assertEquals("""{"a":{"b":2,"c":3},"z":1}""", encoder.encode(JsonObject(mapOf("z" to JsonPrimitive(1), "a" to JsonObject(mapOf("c" to JsonPrimitive(3), "b" to JsonPrimitive(2)))))).decodeToString())
    }
    @Test fun jsonSerializationPreservesBooleansNumbersNullAndStrings() {
        val decoded = JSONSerialization.jsonObject("""{"b":true,"i":9223372036854775807,"d":1.5,"n":null,"s":"3"}""".encodeToByteArray()) as Map<*, *>
        assertEquals(true, decoded["b"])
        assertEquals(Long.MAX_VALUE, decoded["i"])
        assertEquals(1.5, decoded["d"])
        assertNull(decoded["n"])
        assertEquals("3", decoded["s"])
        assertEquals(decoded, JSONSerialization.jsonObject(JSONSerialization.data(decoded)))
        assertFails { JSONSerialization.data(Double.NaN) }
    }
    @Test fun cancellationIsNeverSwallowed() {
        assertFailsWith<CancellationException> { tryOrNull<Unit> { throw CancellationException("cancelled") } }
        assertNull(tryOrNull<Unit> { throw IllegalStateException("ordinary") })
    }
}

class L10nFormatTest {
    @Test fun localizedFormatRetainsPositionsPrecisionAndLiteralPercent() {
        assertEquals("Hello Ada: 3, 1.25, 100%", AppLocalization.format("Hello %@: %lld, %.2lf, 100%%", arrayOf("Ada", 3, 1.25), Locale.US))
        assertEquals("3: Ada", AppLocalization.format("%2\$lld: %1\$@", arrayOf("Ada", 3), Locale.US))
    }
    @Test fun localizationsExcludeDraftsAndUseRFC4647Fallback() {
        val localizations = AppLocalizations(listOf("Base", "en", "zh-Hans", "ZH_hans", "fr", "de"), "en", listOf("de"))
        assertEquals(listOf("en", "fr", "zh-Hans"), localizations.shipped)
        assertEquals("zh-Hans", localizations.match("ZH_hans_CN"))
        assertNull(localizations.match("zh-Hant"))
        assertNull(localizations.match("de-DE"))
    }
    @Test fun scratchPreferencesDoNotTouchTheApplication() {
        val a = UserDefaults.scratch()
        val b = UserDefaults.scratch()
        a.set(byteArrayOf(1, 2), "data")
        a.set(1.25, "double")
        assertContentEquals(byteArrayOf(1, 2), a.data("data"))
        assertEquals(1.25, a.double("double"))
        assertNull(b.data("data"))
        a.removeObject("data")
        assertNull(a.data("data"))
    }
}

class DashboardBoundaryTest {
    @Test fun profileQueriesCannotInjectAnotherParameter() {
        assertEquals("/api/sessions?profile=a%20b%26admin%3Dtrue", DashboardPath.withProfile("/api/sessions", "a b&admin=true"))
        assertEquals("/api/sessions", DashboardPath.withProfile("/api/sessions", "default"))
        assertEquals(emptyList(), DashboardPath.socketProfileItems("default", "conduit_profile"))
        assertEquals(listOf(URLQueryItem("profile", "default")), DashboardPath.socketProfileItems("default", "profile"))
        assertEquals(mapOf("title" to "New", "profile" to "work"), DashboardPath.bodyWithProfile(mapOf("title" to "New"), "work"))
    }
    @Test fun signInRedirectClassificationPreservesProxyBoundary() {
        val dashboard = URI("http://localhost:9119/hermes")
        assertTrue(DashboardSignInRedirect.isSignIn(URI("https://id.example/authorize"), dashboard))
        assertTrue(DashboardSignInRedirect.redirectIsSignIn(" /hermes/login ", dashboard, dashboard))
        assertFalse(DashboardSignInRedirect.isSignIn(URI("https://localhost/hermes"), dashboard))
        assertFalse(DashboardSignInRedirect.isSignIn(URI("https://localhost:9119/hermes"), dashboard))
        assertTrue(DashboardSignInRedirect.isSignIn(URI("https://localhost:8443/hermes"), dashboard))
        assertFalse(DashboardSignInRedirect.isSignInPath("/docs/login-guide"))
        assertTrue(DashboardSignInRedirect.isSignInPath("/hermes/oauth2/start"))
    }
    @Test fun dataURLDecodeIsBoundedStrictAndRejectsEmptyData() {
        assertContentEquals("Hello".encodeToByteArray(), DataURLLimits.decodeBase64DataURL("data:text/plain;base64,SGVsbG8="))
        assertNull(DataURLLimits.decodeBase64DataURL("data:text/plain;base64,%%%"))
        assertNull(DataURLLimits.decodeBase64DataURL("data:text/plain;base64,"))
        assertNull(DataURLLimits.decodeBase64DataURL("data:text/plain;base64,SGVsbG8=", "data:image/"))
        assertFalse(DataURLLimits.isBase64CharacterCountWithinLimit(DataURLLimits.maxBase64Characters + 1))
    }
}
