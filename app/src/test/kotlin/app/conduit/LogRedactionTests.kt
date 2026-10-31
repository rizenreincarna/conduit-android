// Port of: ConduitTests/LogRedactionTests.swift @6ddae94
package app.conduit

import org.junit.Test
import kotlin.test.*

class LogRedactionTests {
    @Test fun testEverySchemeIsRedactedAndPlainTextIsKept() {
        assertEquals(
            LogRedaction.redactingURLs("a http://h/x b wss://h:1/y?z=1 c conduit://connect?url=u d"),
            "a <url> b <url> c <url> d"
        )
        assertEquals(LogRedaction.redactingURLs("The operation couldn’t be completed. (Code 3)"),
                       "The operation couldn’t be completed. (Code 3)")
    }
    @Test fun testBareHostsAndAddressesAreRedacted() {
        assertEquals(
            LogRedaction.redactingURLs("Could not connect to agriass-air.local:9119, then Mac.Local:80."),
            "Could not connect to <host>, then <host>."
        )
        assertEquals(
            LogRedaction.redactingURLs("192.168.1.20:8765 refused; [fe80::1%25en0]:9119 and 100.64.0.1."),
            "<host> refused; <host> and <host>."
        )
        assertEquals(LogRedaction.redactingURLs("foo.tail1234.ts.net timed out"), "<host> timed out")
        assertEquals(
            LogRedaction.redactingURLs("GET backup.example.com/dump?token=abc failed (retry)."),
            "GET <host> failed (retry)."
        )
        assertEquals(LogRedaction.redactingURLs("backup.example.com/dump.json"), "<host>")
        assertEquals(LogRedaction.redactingURLs("example.com?token=abc and my_host.lan"), "<host> and <host>")
    }
    @Test fun testTypeNamesVersionsAndFileNamesAreKept() {
        for (text in listOf(
            "HermesClientError.invalidResponse(statusCode: 500)",
            "Swift.DecodingError.keyNotFound(CodingKeys(stringValue: \"session_id\", intValue: nil))",
            "The file “config.yaml” couldn’t be opened.",
            "Version 1.2.3 at 12:30, item [0], e.g. this.",
            "Could not read data.xml or Base.xcconfig",
            "Error Domain=NSURLErrorDomain Code=-1200 UserInfo={_kCFStreamErrorCodeKey=-9813}",
        )) {
            assertEquals(LogRedaction.redactingURLs(text), text)
        }
    }
    @Test fun testPunctuationInsideAURLIsRedactedButNotAfterIt() {
        assertEquals(
            LogRedaction.redactingURLs("see https://h/api/a,b;c?token=secret (retry)."),
            "see <url> (retry)."
        )
        assertEquals(
            LogRedaction.redactingURLs("(at https://h/p). Next: http://[fe80::1%25en0]:9119/api?token=x]"),
            "(at <url>). Next: <url>]"
        )
    }
    @Test fun testUserInfoEntriesStaySeparated() {
        assertEquals(
            LogRedaction.redactingURLs("{NSErrorFailingURLStringKey=https://h/a, NSErrorFailingURLKey=https://h/a}"),
            "{NSErrorFailingURLStringKey=<url>, NSErrorFailingURLKey=<url>}"
        )
    }
    // android: NSError/URLError are represented by Throwable with the same diagnostic text.
    @Test fun testMessageRedactsAnAddressInTheLocalizedDescription() {
        assertEquals("Couldn't reach <url>, giving up", LogRedaction.message(Exception("Couldn't reach ws://192.168.1.20:8765/v1/realtime, giving up")))
    }
    @Test fun testAnAbsentErrorReadsNil() { assertEquals("nil", LogRedaction.describing(null)) }
    @Test fun testATLSErrorLosesTheHostItDistrusts() {
        assertEquals("pretending to be “<host>” which", LogRedaction.redactingURLs("pretending to be “localhost” which"))
    }
    // skipped: testSourcesLogSessionIdsHashedOnOneSubsystem inspects Swift OSLog interpolation.
    // skipped: testURLErrorDescriptionKeepsItsCodeAndDropsTheAddress constructs Foundation URLError.
}
