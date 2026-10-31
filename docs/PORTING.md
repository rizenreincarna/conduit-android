# Porting guide: Conduit Swift → Kotlin

Every ported file follows this guide. The spec
(`docs/superpowers/specs/2026-11-01-conduit-android-core-design.md`) says *what* is
ported; this says *how*. Upstream checkout: `.upstream/hermes-conduit` at `6ddae94`.

The goal is a line-for-line port: same types, same names, same control flow, same
comments (trimmed of iOS-only detail). A reader holding the Swift file next to the
Kotlin file should find every function in the same order. Do not "improve" upstream
logic, merge functions, or drop branches that look dead; the only allowed changes are
the ones this guide names.

## 1. Ground rules

- **Header.** First line of every ported file: `// Port of: <upstream path> @6ddae94`
  (e.g. `// Port of: Conduit/Services/ConnectionURLPolicy.swift @6ddae94`). A file
  built from several Swift files lists each on its own `// Port of:` line.
- **Package.** Every file declares `package app.conduit`, whatever its folder. Ported
  files never import each other; they import only Android/Kotlin/library packages.
- **Path.** Under `app/src/main/kotlin/app/conduit/`: `Conduit/` maps to the root and
  `Shared/` to `shared/`; every directory is lowercased and `+` is dropped from file
  names. `Conduit/Views/V2/V2Shell.swift` → `views/v2/V2Shell.kt`,
  `Conduit/Theme.swift` → `Theme.kt`, `Shared/WatchVoice/CustomAccentColor.swift` →
  `shared/watchvoice/CustomAccentColor.kt`, `Conduit/Services/AppState+PendingSend.swift`
  → `services/AppStatePendingSend.kt`. `scripts/portmap.py path <swift path>` prints it;
  exceptions live in `upstream/port-map.json`.
- **Names.** Types, functions, properties, enum cases and parameters keep their Swift
  names verbatim, including lower-camel enum cases (`ConnectionURLPolicyError.invalidURL`).
  Kotlin parameter name = the Swift *internal* name (`func send(_ text: String, to session: S)`
  → `fun send(text: String, session: S)`). If two Swift overloads collide in Kotlin
  (same name and parameter types, different labels), append the first label in
  PascalCase to each: `restore(into:)`/`restore(from:)` → `restoreInto`/`restoreFrom`.
- **Markers.** `// android: <why>` where Android mechanics differ from upstream;
  `// later:<sub-project>` where Core code names, calls into or shows something owned
  by a later sub-project (omitted, commented out or hidden, never faked; §13);
  `// ponytail: <ceiling>` for a deliberate simplification.
- **Unported bodies.** `TODO("port")` is the only placeholder. A merged body batch
  contains none.
- **Visibility.** File-level `private`/`fileprivate` → `private`. Type-member `private`
  → `private` when every use is inside the Kotlin class; otherwise `internal`.
  AppState members are never `private` (see §2). Drop `public`/`open`/`final`.
- **Platform conditionals.** `#if os(iOS)` / `!targetEnvironment(macCatalyst)` → keep
  the iPhone branch; `#if DEBUG` → `if (BuildConfig.DEBUG)`; `#Preview` → drop.

## 2. Files, AppState, nesting

- One Swift file → one Kotlin file. Kotlin file names are unique module-wide
  (one package ⇒ one `<Name>Kt` facade class per file name).
- **AppState** (`Conduit/Services/AppState.swift`):
  - `services/AppState.kt`: `class AppState` with every stored property Core code
    reads or writes, whatever `MARK` it sits under, each with its observers
    (`didSet`/`willSet` → custom setter: the backing `field` is reachable only inside
    the class), every nested type Core uses, and `init`. Stored properties are plain
    `var` (`private(set)` dropped) so section files can assign them.
  - Each class-level `// MARK:` line (with or without `-`) starts a section →
    `services/appstate/AppState_<PascalTitle>.kt` holding that section's Core methods
    and computed properties as extension members (`fun AppState.foo()`,
    `val AppState.bar: T get() = …`) and one `// later:` line for its later-owned
    members (§13). Every section gets a file, even one with only `// later:` lines.
    PascalTitle: drop a leading `- `; keep the text before the first ` (` or `:`; drop
    `'` and `` ` ``; split on non-alphanumerics; uppercase each word's first letter, keep
    the rest. A repeated title gets `2`. `UI state` → `AppState_UIState.kt`,
    `Sending while connecting: what reads private state` →
    `AppState_SendingWhileConnecting2.kt`. `scripts/portmap.py` implements this.
  - Lines 1–586 (before `class AppState`) → `services/appstate/AppState_Support.kt`.
    Each file-level `MARK` after the class is a section too
    (`AppState_LiveTurnProjection.kt`, `AppState_AttachmentHelper.kt`,
    `AppState_AllProfilesInOneChatsList.kt`), except `Keychain Helper` →
    `services/KeychainHelper.kt`.
  - AppState `private`/`fileprivate` members → `internal`.
- **Extensions** of a type → Kotlin extension functions/properties in the file where
  the Swift extension lives. An extension that adds a protocol conformance moves the
  conformance and its members into the type's own declaration (comment
  `// from <Swift file>`).
- **Nested types** of structs/classes/enums nest the same way in Kotlin
  (`Foo.Bar`). Types nested in a SwiftUI `View` go inside `object <ViewName> { … }`
  (private ones become `internal`) so `ViewName.Row` still reads the same. A View
  nested in another type is a composable inside that type's object
  (`object ChatView { @Composable fun Header(…) }`).

## 3. Types

| Swift | Kotlin |
|---|---|
| `struct` | `data class` with `val`s, mutation via `copy(...)`. A struct with no stored properties → `object` (or `class` if it has `init`). |
| `mutating func f(...)` | `fun f(...): Self` returning the new value; call sites `x = x.f(...)`. If it also returns `R`: `Pair<Self, R>`. |
| `inout s: Struct` | parameter + return the new value (`Pair`/`Triple` for several). `inout [T]` / `inout [K: V]` → `MutableList<T>` / `MutableMap<K, V>` parameter. |
| `class` / `final class` | `class` (add `open` only if upstream subclasses it). |
| `enum` without payloads | `enum class`, cases verbatim. `CaseIterable` → `entries`. |
| `enum X: String` (raw values) | `enum class X(val rawValue: String) { a("a"), bB("b_b"); companion object { operator fun invoke(rawValue: String): X? = entries.firstOrNull { it.rawValue == rawValue } } }`; `@Serializable` + `@SerialName("<raw>")` per case when Codable. Same with `Int` raw values. |
| `enum` with payloads | `sealed interface X` with `data class`/`data object` cases named verbatim; `switch` → exhaustive `when (x) { is X.case -> … }`. |
| `enum` used as namespace (no cases) | `object` |
| `enum X: Error` / `struct X: Error` | `sealed class X : Exception()` with `data object`/`data class` cases / `class X : Exception()`. `LocalizedError` → also implement `LocalizedError` (`override val errorDescription: String?`). |
| `protocol` | `interface`; protocol-extension defaults → default methods. `associatedtype` (none in Core). |
| `static let/var` | `companion object` members (file-scope constants: top-level `private val`). `static func` → companion `fun`. |
| `lazy var` | `val x by lazy { … }` (`lazy(LazyThreadSafetyMode.NONE)` on main-thread classes) |
| `didSet` / `willSet` | backing property + custom setter: `private var _x = v; var x: T get() = _x; set(value) { val oldValue = _x; _x = value; /* didSet body */ }` (for state: `private var _x by mutableStateOf(v)`). |
| extra `init(...)` | secondary `constructor` when it can delegate to the primary; otherwise companion `operator fun invoke(...)` (keeps `Type(args)` call syntax). `init?` → companion `operator fun invoke(...): Type?`. |
| `deinit` | the body moves to `fun close()` (or the owner's teardown) called where upstream releases the object; comment `// android: deinit`. |
| `weak var x` / `[weak self]` | strong references; drop `guard let self`. Staleness is handled by upstream's epoch/identity fences, which are ported as-is. |
| `Equatable`/`Hashable` | data classes get them free. A data class holding a `ByteArray` overrides `equals`/`hashCode` with `contentEquals`/`contentHashCode`. Classes implementing `==` → `equals`/`hashCode`. |
| `Comparable` | `Comparable<T>` + `compareTo` |
| `CustomStringConvertible` | `override fun toString()` (keep `description` as a property if code reads it). |
| `Identifiable` | foundation `interface Identifiable<ID> { val id: ID }` |
| `typealias` | `typealias` |
| generics, `where` | Kotlin generics; `some P` / `any P` → `P` |
| `Optional` | nullable; `if let`/`guard let` → `val x = y ?: return` / `?.let`; `??` → `?:`; `x!` → `x!!`. |
| `subscript` | `operator fun get`/`set` |
| `KeyPath` (`\.name`) | lambda `{ it.name }` |

## 4. Values and the standard library

- **Numbers.** Swift `Int` → `Int`, except epoch milliseconds, byte counts, file sizes
  and server-provided integers → `Long` (server JSON: `Long` when unsure).
  `Double`/`Float`/`CGFloat` → `Double`/`Float`/`Float` (CGFloat points = dp).
  `x.rounded()` → `x.swiftRounded()` (half away from zero). Integer division and `%`
  match. `Int(d)` truncates in both. `Int(string)` → `string.toIntOrNull()`.
  Overflowing `&+` → plain `+`.
- **Strings.** Swift counts grapheme clusters; Kotlin counts UTF-16 units. Use the
  foundation helpers for every character-level operation: `s.count` → `s.swiftCount`,
  `s.prefix(n)`/`suffix(n)`/`dropFirst(n)`/`dropLast(n)` → `swiftPrefix`/`swiftSuffix`/
  `swiftDropFirst`/`swiftDropLast`; `s.first`/`last` (Character) → `swiftFirst`/
  `swiftLast` (String?). `utf16.count` → `length`; `utf8.count` →
  `encodeToByteArray().size`. `isEmpty` same. `hasPrefix`/`hasSuffix` →
  `startsWith`/`endsWith`. `trimmingCharacters(in: .whitespacesAndNewlines)` → `trim()`;
  `.whitespaces` → `trim { it == ' ' || it == '\t' }`. `lowercased()` → `lowercase()`.
  `components(separatedBy: x)` → `split(x)`; `split(separator: x)` →
  `split(x).filter { it.isNotEmpty() }` (Swift omits empty pieces);
  `split(separator:omittingEmptySubsequences: false)` → `split(x)`.
  `replacingOccurrences(of:with:)` → `replace`. `contains(x)` same.
  `localizedCaseInsensitiveContains` → `contains(x, ignoreCase = true)`;
  `localizedStandardContains` / `localizedStandardCompare` /
  `caseInsensitiveCompare` → foundation helpers of the same name.
  `String(format: f, args)` → `String.format(Locale.ROOT, f', args)` with the specifier
  rewritten (`%@`→`%s`, `%ld`/`%lld`→`%d`, `%lf`→`%f`). Interpolation → templates.
  `Character` → `String` (one grapheme) unless upstream does arithmetic on it.
- **Collections.** `[T]` → `List<T>`, `[K: V]` → `Map<K, V>`, `Set<T>` → `Set<T>`.
  Stored and published collections are immutable and reassigned
  (`messages = messages + m`, `map = map + (k to v)`, `map - k`). Locals built in a loop
  → `mutableListOf()`/`mutableMapOf()`. `first`/`last` → `firstOrNull()`/`lastOrNull()`;
  `first(where:)` → `firstOrNull {}`; `removeFirst()` → `removeAt(0)`;
  `compactMap` → `mapNotNull`; `flatMap` → `flatMap`; `reduce(x)` → `fold(x)`;
  `enumerated()` → `withIndex()`; `zip` same; `sorted(by:)` → `sortedWith { a, b -> … }`
  (stable in both); `min(by:)` → `minWithOrNull`; `prefix(n)` on arrays → `take(n)`;
  `dropFirst(n)` → `drop(n)`; `suffix(n)` → `takeLast(n)`; `reversed()` same;
  `indices` same; `isEmpty` same; `allSatisfy` → `all`; `contains(where:)` → `any`;
  `firstIndex(of:)` → `indexOf` (-1 → null: `.takeIf { it >= 0 }`); `Dictionary(grouping:by:)`
  → `groupBy`; `Dictionary(uniqueKeysWithValues:)` → `toMap()`; `dict[k, default: d]`
  → `dict[k] ?: d`; `dict.keys`/`values` → `keys`/`values`; `mapValues` same.
  Swift arrays trap on out-of-range indexes; Kotlin throws — same contract.
- **Dates.** `Date` → `java.time.Instant`; `TimeInterval` → `Double` seconds;
  `Date()` → `Instant.now()`. Foundation helpers: `Instant.timeIntervalSince1970`,
  `timeIntervalSince(other)`, `timeIntervalSinceNow`, `addingTimeInterval(t)`,
  `instantFromEpochSeconds(d)`, `distantPast`, `distantFuture`. `Duration` →
  `kotlin.time.Duration`. Monotonic clocks: `ContinuousClock`/`SuspendingClock` →
  `TimeSource.Monotonic`; `ProcessInfo.processInfo.systemUptime` →
  `SystemClock.elapsedRealtime() / 1000.0` via the `ProcessInfo` shim.
- **Data.** `Data` → `ByteArray` (identity equality! see §3). `String(data:encoding: .utf8)`
  → `bytes.utf8String()` (null on invalid UTF-8, like Swift); `Data(s.utf8)` →
  `s.encodeToByteArray()`; base64 → `java.util.Base64`.
- **UUID** → `java.util.UUID`; `UUID()` → `UUID.randomUUID()`; `uuidString` →
  `uuid.uuidString` (foundation: uppercase, like Swift); `UUID(uuidString:)` →
  `uuidFromString(s)` (strict 8-4-4-4-12, null otherwise).
- **URL** → `java.net.URI`; file URLs → `java.io.File`. `URL(string:)` →
  `urlFromString(s)` (null when invalid); `absoluteString`, `appendingPathComponent`,
  `appending(queryItems:)`, `lastPathComponent`, `deletingLastPathComponent()`,
  `portOrNull` are foundation extensions on `URI`. `URLComponents` → foundation class.
- **Errors.** `throws` disappears (unchecked exceptions). `try x()` → `x()`;
  `try? x()` → `tryOrNull { x() }`; `do/catch` → `try/catch`; typed `catch let e as E`
  → `catch (e: E)`. Every catch-all (`catch (e: Exception)`/`Throwable`) and `tryOrNull`
  rethrows `CancellationException`. `error.localizedDescription` →
  `error.localizedDescription` (foundation extension: `LocalizedError.errorDescription`,
  else the message). `Result<T, E>` → `kotlin.Result<T>`. `fatalError(msg)`,
  `precondition(x)`, `preconditionFailure(msg)`, `assert(x)`, `assertionFailure(msg)` →
  same-named foundation functions (`assert*` only in DEBUG; the others throw
  `IllegalStateException`).
  `defer { }` → `try { … } finally { }`.
- **Comparisons.** `ComparisonResult` → `Int` sign: `a.compare(b) == .orderedAscending`
  → `a.compareTo(b) < 0`; the foundation `localizedStandardCompare`/`caseInsensitiveCompare`
  return `Int`. `Calendar.current.isDateInToday(d)` → `isDateInToday(d)` (foundation;
  `java.time` in `ZoneId.systemDefault()`).
- **URL parts.** `url.host` → `url.swiftHost` (IPv6 without brackets, like Foundation);
  `url.port` → `url.portOrNull`; `url.query` → `url.rawQuery`; `url.fragment` →
  `url.rawFragment`; `url.path` → `url.path`; `url.scheme` → `url.scheme`.
- **Regex.** `NSRegularExpression` → `kotlin.text.Regex` (`.caseInsensitive` →
  `IGNORE_CASE`, `.anchorsMatchLines` → `MULTILINE`, `.dotMatchesLineSeparators` →
  `DOT_MATCHES_ALL`); `NSRange`s are UTF-16 offsets, same as Kotlin indexes;
  `firstMatch` → `find`, `matches` → `findAll`, `range(at: i)` → `groups[i]?.range`.
  Swift `Regex`/`#/…/#` → `Regex`. `CharacterSet` → char predicates.
- **Crypto/random.** `SHA256.hash(data:)` → `MessageDigest.getInstance("SHA-256")`;
  hex via foundation `ByteArray.hexString()`; `SecRandomCopyBytes` → `SecureRandom`;
  `Int.random(in:)` → `(a..b).random()`.

## 5. JSON and Codable

- One configured instance: `ConduitJson` (`ignoreUnknownKeys = true`,
  `explicitNulls = false`, `encodeDefaults = true`, `coerceInputValues = false`).
  `JSONDecoder().decode(T.self, from: d)` → `JSONDecoder().decode<T>(d)`;
  `JSONEncoder().encode(x)` → `JSONEncoder().encode(x)` (foundation shims over
  `ConduitJson`, `ByteArray` in/out; `outputFormatting = .sortedKeys` supported).
- `Codable` struct → `@Serializable data class`; optional properties `= null`;
  `CodingKeys` renames → `@SerialName`. Properties excluded from `CodingKeys` →
  `@Transient` with their default.
- `Date` fields: Swift's default `JSONEncoder` writes seconds since 2001-01-01, so
  files holding `Instant`s in `@Serializable` types start with
  `@file:UseSerializers(ReferenceDateInstantSerializer::class)`. Fields upstream codes
  differently use `@Serializable(with = EpochSecondsInstantSerializer::class)` or
  `Iso8601InstantSerializer`. `UUID` → `UUIDSerializer` (uppercase out, any case in);
  `Data` → `Base64ByteArraySerializer`; `URL` → `URISerializer`.
- Custom `init(from decoder:)` / `encode(to:)`: keep the generated serializer and put a
  Swift-shaped one in front of it. The `CodingKeys` enum is ported as a nested
  `enum class CodingKeys(override val stringValue: String) : CodingKey`.
  ```kotlin
  @OptIn(ExperimentalSerializationApi::class)
  @KeepGeneratedSerializer
  @Serializable(with = SessionInfoSerializer::class)
  data class SessionInfo(@SerialName("session_id") val sessionID: String, val title: String? = null) {
      enum class CodingKeys(override val stringValue: String) : CodingKey { sessionID("session_id"), title("title") }
  }
  object SessionInfoSerializer : SwiftCodableSerializer<SessionInfo>(SessionInfo.generatedSerializer()) {
      override fun decode(decoder: SwiftDecoder): SessionInfo {        // init(from decoder:)
          val c = decoder.container()
          return SessionInfo(sessionID = c.decode(SessionInfo.CodingKeys.sessionID),
                             title = c.decodeIfPresent(SessionInfo.CodingKeys.title))
      }
  }
  ```
  Override `encode(value: T, encoder: SwiftEncoder)` only where upstream writes
  `encode(to:)`; otherwise encoding stays the generated (synthesized) one, so the
  `@SerialName`s must match `CodingKeys`. API, mirroring Swift: `decoder.container()`
  → `decode<T>(key)`, `decodeIfPresent<T>(key)` (missing or `null` → `null`; present
  with the wrong type throws), `contains(key)`, `decodeNil(key)`, `allKeys`,
  `nestedContainer(key)`, `nestedUnkeyedContainer(key)`; `decoder.singleValueContainer()`
  → `decode<T>()`, `decodeNil()`; `decoder.unkeyedContainer()` → `isAtEnd`, `count`,
  `decode<T>()`, `decodeIfPresent<T>()`. `encoder.container()` → `encode(value, key)`,
  `encodeIfPresent(value, key)`, `encodeNil(key)`; `encoder.singleValueContainer().encode(v)`.
  Primitives decode with Swift's strictness (an `Int` only from an integral JSON number,
  a `String` only from a JSON string, a `Bool` only from `true`/`false`); other types go
  through their own serializer. Errors are `DecodingError.keyNotFound/typeMismatch/
  valueNotFound/dataCorrupted`. `try?` around a decode stays `tryOrNull { }`.
- Encoded bytes can differ from Swift's (Swift escapes `/`; key order is declaration
  order). Nothing may depend on exact bytes except where upstream sets `.sortedKeys`,
  which the `JSONEncoder` shim honors.
- `JSONSerialization.jsonObject(with:)` → `JSONSerialization.jsonObject(data)`
  returning `Map<String, Any?>` / `List<Any?>` / `String` / `Long` (integral) /
  `Double` / `Boolean` / `NSNull`. Casts mirror Foundation's NSNumber bridging through
  foundation getters: `x as? Int` → `x.asInt()`, `as? Double` → `asDouble()`,
  `as? Bool` → `asBool()`, `as? String` → `asString()`, `as? [String: Any]` →
  `asDict()`, `as? [[String: Any]]` → `asDictArray()`, `as? [Any]` → `asArray()`,
  `as? Int64` → `asLong()`. `JSONSerialization.data(withJSONObject:)` →
  `JSONSerialization.data(obj)`. Upstream's `AnyCodable` is ported as written.

## 6. Concurrency

The app runs on the main thread like upstream's `@MainActor`. Swift `Task {}`
enqueues; Kotlin `Dispatchers.Main.immediate` would run inline and reorder side
effects — so plain launches use `Dispatchers.Main`.

| Swift | Kotlin |
|---|---|
| `@MainActor class` | class whose members are called on main; `val scope = MainScope()` if it launches work (AppState: `AppState.scope`). |
| `Task { … }` (main-actor context or `@MainActor in`) | `scope.launch { … }` (scope dispatcher = `Dispatchers.Main`) |
| `Task { … }` from nonisolated code, `Task.detached` | `scope.launch(Dispatchers.Default) { … }` |
| `let t: Task<T, Error>` | `Deferred<T>` (`scope.async`) when `.value` is read, else `Job`; `t.cancel()` → `cancel()`; `await t.value` → `await()` |
| `await MainActor.run { }` / hop to `@MainActor` | `withContext(Dispatchers.Main.immediate) { }` |
| `MainActor.assumeIsolated { }` | run inline |
| `DispatchQueue.main.async { }` | `mainScope.launch { }` (foundation `mainScope`) |
| `DispatchQueue.main.asyncAfter(deadline: .now() + s)` | `mainScope.launch { delay(s.seconds); … }` |
| `DispatchQueue(label:)` | `Dispatchers.Default.limitedParallelism(1)` |
| `actor` | class + `Dispatchers.Default.limitedParallelism(1)` (none in Core) |
| `nonisolated`, `@Sendable`, `sending` | drop |
| `Task.sleep(for: .seconds(x))` / `(nanoseconds:)` | `delay(x.seconds)` / `delay(n / 1_000_000)`. `try? await Task.sleep` → `delay` (cancellation ends the coroutine; upstream follows these with a cancellation guard). |
| `Task.isCancelled` / `try Task.checkCancellation()` | `!currentCoroutineContext().isActive` / `currentCoroutineContext().ensureActive()` |
| `CancellationError` | `CancellationException` |
| `withCheckedContinuation` / `withCheckedThrowingContinuation` | `suspendCoroutine` (Swift continuations are not cancellable) |
| `withTaskCancellationHandler { withChecked…Continuation } onCancel: { }` | `suspendCancellableCoroutine { c -> c.invokeOnCancellation { … } }`; other `withTaskCancellationHandler` → `try { } finally { if (!isActive) onCancel() }` |
| `withTaskGroup` / `async let` | `coroutineScope { val a = async { }; … }` |
| `NSLock` | `ReentrantLock` (`lock()`/`unlock()`/`withLock {}`) |
| Combine `sink`, `Timer.publish`, `NotificationCenter.publisher` | `Flow` + `collect` in a launched job |
| `Timer.scheduledTimer` | launched loop with `delay` |

WebSocket frames ≥ 16 KB decode on `Dispatchers.Default`, as upstream. Never block
the main thread on I/O: URLSession shims are `suspend` and run on `Dispatchers.IO`.

## 7. Platform mapping (foundation shims in `app/conduit/foundation/`)

| Upstream | Android |
|---|---|
| `UserDefaults.standard` | `UserDefaults.standard` (SharedPreferences `conduit.defaults`, same keys, snapshot-state cache; `UserDefaults.scratch()` in tests) |
| `KeychainHelper` | `KeychainHelper` (same API and accounts; `KeystoreKeychainBackend`, AES-GCM key in AndroidKeyStore) |
| `FileManager.default` | `FileManager.default` shim: `applicationSupportDirectory` = `filesDir`, `cachesDirectory` = `cacheDir`, `temporaryDirectory` = `cacheDir/tmp`, `documentDirectory` = `filesDir/Documents`; `fileExists`, `createDirectory`, `removeItem`, `moveItem`, `copyItem`, `contentsOfDirectory`, `attributesOfItem`. `Data(contentsOf:)` → `file.readBytes()`; `write(to:options: .atomic)` → `writeAtomically(file, bytes)` (`AtomicFile`). |
| `NotificationCenter.default` | `NotificationCenter.default` shim (`addObserver(forName:queue:block)` → token, `removeObserver`, `post`, `publisher(name): Flow`). `UIApplication.didBecomeActiveNotification` etc. are posted from `MainActivity` lifecycle. |
| `UIApplication.shared` | shim: `open(url)` → `ACTION_VIEW`; `applicationState`; `isIdleTimerDisabled` → `FLAG_KEEP_SCREEN_ON`; `preferredContentSizeCategory` from font scale; `beginBackgroundTask`/`endBackgroundTask` → no-op ids; `sendAction(resignFirstResponder)` → `endEditing()` (clears focus, hides IME); `openSettingsURLString` → app details settings; `supportsAlternateIcons = false`; `registerForRemoteNotifications` → `// later:notifications`. |
| `UIDevice.current` | `model` = `Build.MODEL`, `systemName` = "Android", `systemVersion` = `Build.VERSION.RELEASE`, `name` = device name, `userInterfaceIdiom` = `.pad` when smallest width ≥ 600dp else `.phone`, `identifierForVendor` = random UUID kept in prefs. |
| `ProcessInfo.processInfo` | `systemUptime`, `isLowPowerModeEnabled` (power save), `thermalState` (thermal status), `environment`, `arguments` (empty), `isiOSAppOnMac = false`. |
| `Bundle.main` | `CFBundleShortVersionString` → `BuildConfig.VERSION_NAME`, `CFBundleVersion` → `BuildConfig.VERSION_CODE.toString()`, `bundleIdentifier` → `BuildConfig.APPLICATION_ID`; resources → `assets/`. |
| `UIPasteboard.general` | `ClipboardManager` (`string`, `strings`, `hasStrings`, `image`, `url`) |
| `os.Logger(subsystem:category:)` | `Logger(subsystem, category)` → `android.util.Log`, tag = category. Interpolations with `privacy: .public` → `$x`; dynamic strings without it → the literal `<private>` (os_log's default); numbers and Bools are public. |
| `URLSession`, `URLRequest`, `HTTPURLResponse`, `URLError`, `URLComponents`, `HTTPCookie(Storage)`, `URLSessionWebSocketTask` | networking shims over OkHttp (same API; `URLRequest`/`URLComponents` are mutable classes — call `.copy()` where Swift copies a `var` before mutating) |
| `WKWebView` | `android.webkit.WebView` in `AndroidView`; one `WebView` profile per dashboard (`WebViewCompat` multi-profile); `WKUserScript` → `WebViewCompat.addDocumentStartJavaScript`; `messageHandlers[...]` → `WebViewCompat.addWebMessageListener` restricted to the dashboard origin; `decidePolicyFor navigationAction` → `shouldOverrideUrlLoading` + `shouldInterceptRequest` keeping upstream's main-frame/subframe rules; cookies → `CookieManager`. |
| `ASWebAuthenticationSession` | Custom Tabs + `conduit://` redirect intent filter |
| `NWPathMonitor` | `ConnectivityManager.registerDefaultNetworkCallback` |
| `scenePhase` | `LocalScenePhase` fed by `MainActivity` (`onResume` active, `onPause` inactive, `onStop` background) |
| `LAContext` (Face ID) | `BiometricPrompt` |
| `UIImpactFeedbackGenerator` etc. | `View.performHapticFeedback` / `Vibrator` (in `Haptics`) |
| `PhotosPicker` / camera / document picker | `PickVisualMedia` / `TakePicture` (after `CAMERA` permission) / `OpenDocument` |
| `DataScannerViewController` (QR) | CameraX `ImageAnalysis` + zxing `QRCodeReader` |
| QuickLook | `ACTION_VIEW` + `FileProvider` |
| `ISO8601DateFormatter`, `DateFormatter`, `.formatted(...)`, `RelativeDateTimeFormatter`, `ByteCountFormatter`, `ListFormatter`, `NumberFormatter` | foundation formatters with the same names/forms, built on `java.time`/`android.icu`, locale = `AppLocalization.formattingLocale` |
| `UIImage` | `android.graphics.Bitmap` (+ foundation helpers for JPEG/PNG data and size) |
| `UIColor`/`Color` | Compose `Color`; iOS semantic colors from `Theme.kt` (`Color.label`, `.secondaryLabel`, `.systemBackground`, …) with iOS light/dark values |
| `UIFont`/`Font` text styles | `scaledFont(...)` (§8) |
| StoreKit review prompt | no-op `// android:` |
| `UIApplication.shared.connectedScenes` / multi-window | single activity; drop |

## 8. SwiftUI → Compose

### Views
- **Stateless view** (no `@State`, `@StateObject`, `@FocusState`, `@AppStorage`,
  `@GestureState`, `@Namespace`): one `@Composable fun Name(params)`; Swift stored
  properties → parameters in declaration order (closures last); `body` inline;
  environment read inline (`LocalX.current`); computed properties → local `val`s or
  private functions; private helper views → private composables in the file.
- **Stateful view**: entry `@Composable fun Name(params)` + `class NameState(…)`
  (a plain state holder, not a ViewModel):
  ```kotlin
  @Composable
  fun CustomHeadersEditorSheet(serverURL: String, headers: List<CustomHeader>, onSave: (List<CustomHeader>) -> Unit) {
      val s = remember { CustomHeadersEditorSheetState(headers) }  // the Swift init's State(initialValue:) inputs
      s.dismiss = LocalDismiss.current                             // every @Environment / @EnvironmentObject
      s.serverURL = serverURL                                      // every stored let/var, on each composition
      s.onSave = onSave
      s.Body()
  }
  class CustomHeadersEditorSheetState(headers: List<CustomHeader>) {
      var dismiss: DismissAction = DismissAction.none
      var serverURL = ""
      var onSave: (List<CustomHeader>) -> Unit = {}
      var rows by mutableStateOf(headers)                          // @State private var rows
      var revealedValues by mutableStateOf(setOf<UUID>())          // @State private var revealedValues
      val origin: String? get() = CustomHeaderPolicy.origin(serverURL)  // computed property
      fun issue(row: CustomHeader): CustomHeaderIssue? = …              // private func issue(for:)
      @Composable fun Body() { … }                                      // var body
  }
  ```
  Sub-view properties and `@ViewBuilder` functions → `@Composable` members with the
  same names. `@StateObject var m = M()` → holder field `val m = M()` (the holder is
  remembered); `@ObservedObject`/`@Bindable` → ordinary assigned field.
  `@AppStorage(key) var x = d` → `var x by AppStorage(key, d)` (foundation delegate over
  `UserDefaults.standard`; the defaults cache is snapshot state, so composables reading
  it recompose on change — works as a holder field or a local). `@FocusState` →
  `FocusRequester` + focused flag. `.onAppear`/`.task` stay in `Body()`.
- **`ObservableObject` / `@Observable` classes**: every `@Published` (or tracked) `var`
  → `var x by mutableStateOf(v)` (`mutableIntStateOf` etc. not needed); plain vars stay
  plain; `objectWillChange.send()` → `changeTick++` on a `var changeTick by
  mutableIntStateOf(0)` that observing composables read.
- **Bindings.** `@Binding var x: T` → parameter `x: Binding<T>` (read `x.value`,
  write `x.value = …`); `$x` → `Binding({ x }, { x = it })`; `$x.field` →
  `Binding({ x.field }, { x = x.copy(field = it) })`; `.constant(v)` →
  `Binding.constant(v)`.
- **Environment.** `@EnvironmentObject var appState: AppState` → `LocalAppState.current`.
  `@Environment(\.key)` → `LocalKey.current` (PascalCase key: `\.dismiss` →
  `LocalDismiss`, `\.scenePhase` → `LocalScenePhase`, `\.colorScheme` →
  `LocalColorScheme`, `\.chatTextSize` → `LocalChatTextSize`). Built-in keys live in
  `foundation/ui/Environment.kt`; upstream's custom `EnvironmentKey`s are ported where
  they are declared, as `val LocalX = compositionLocalOf { default }` (keep the key
  struct name in a comment). `.environment(\.key, v)` / `.environmentObject(o)` →
  `CompositionLocalProvider(LocalKey provides v) { … }`.
- **Lifecycle.** `.onAppear { }` → `OnAppear { }`; `.onDisappear` → `OnDisappear { }`;
  `.task { }` → `LaunchedEffect(Unit) { }`; `.task(id: v)` → `LaunchedEffect(v) { }`;
  `.onChange(of: v) { old, new in }` → `OnChange(v) { old, new -> }` (skips the first
  composition, like SwiftUI); `.onReceive(publisher)` → `LaunchedEffect(Unit) { flow.collect { } }`.
- **Modifiers.** Draw/layout-only modifiers → `Modifier` chain, in Swift order
  (`.padding(.horizontal, 16)` → `.padding(horizontal = 16.dp)`; `.frame(width:height:)` →
  `.size`; `.frame(maxWidth: .infinity)` → `.fillMaxWidth()`;
  `.background(…)`/`.overlay(…)` with shapes → `.background(color, shape)`/`Box` layering;
  `.clipShape` → `.clip`; `.opacity` → `.alpha`; `.offset`; `.rotationEffect` →
  `.rotate`; `.contentShape` + `.onTapGesture` → `.clickable(indication = null)`).
  Upstream `ViewModifier`s that only draw/lay out → `fun Modifier.name(…): Modifier`;
  ones that add sheets, overlays, state or logic → wrapper composable
  `Name(params) { content }`. `.disabled(x)` → `enabled = !x` / `LocalIsEnabled`.
- **Layout.** `VStack(alignment:spacing:)` → `Column(horizontalAlignment, verticalArrangement = Arrangement.spacedBy(s.dp))`
  (SwiftUI's default spacing ≈ 8dp: write `8.dp` where upstream passes none);
  `HStack` → `Row(verticalAlignment = CenterVertically)`; `ZStack` → `Box`;
  `Spacer()` → `Spacer(Modifier.weight(1f))` in Row/Column; `Group` → inline;
  `ScrollView` → `Column(Modifier.verticalScroll(rememberScrollState()))`;
  `LazyVStack`/`List`+`ForEach` → `LazyColumn` + `items(key = { it.id })`;
  `ScrollViewReader` → `LazyListState`; `GeometryReader` → `BoxWithConstraints`
  (or `onSizeChanged`); `ViewThatFits` → `BoxWithConstraints` choice; custom `Layout`
  → `Layout { measurables, constraints -> }`; `TimelineView` → `produceState` ticker;
  `PreferenceKey` → callbacks/`onGloballyPositioned` up the tree.
- **Controls.** `Button(action:) { label }` → `Button`/`Modifier.clickable` matching
  upstream's style (plain/borderless → clickable content; `.borderedProminent` →
  `Button`); `Toggle` → `ToggleRow` (`Switch`); `Picker` → `PickerRow`/`ConduitMenu`;
  `Menu` → `ConduitMenu`; `TextField`/`SecureField`/`TextEditor` → `TextField`/
  `BasicTextField` (+ `PasswordVisualTransformation`); `Label(t, systemImage: s)` →
  `Label(t, systemImage = s)` (UI kit); `ProgressView()` → `CircularProgressIndicator`;
  `ProgressView(value:)` → `LinearProgressIndicator`; `Link` → clickable `openURL`.
- **Navigation and presentation (UI kit).** `NavigationStack { }` →
  `NavigationStack { }` (UI kit over Navigation 3, with `NavigationTitle(t)`,
  `Toolbar { ToolbarItem(placement) { } }`, `NavigationLink`, `navigationDestination`);
  system back pops it. `.sheet(isPresented:/item:)` → `ConduitSheet(...)`
  (`ModalBottomSheet`, full height unless upstream sets detents, back dismisses,
  `onDismiss` kept); `.fullScreenCover` → `ConduitFullScreenCover`; `.popover` →
  `ConduitSheet`; `.alert` → `ConduitAlert` (`AlertButton(role)`); `.confirmationDialog`
  → `ConfirmationDialog`; `.contextMenu` → `Modifier.contextMenu { }` (long press →
  `DropdownMenu`); `.swipeActions` → `SwipeActions`; `.refreshable` → `PullToRefreshBox`;
  `Form`/`Section` → `Form`/`Section` (UI kit, iOS inset-grouped look);
  `ContentUnavailableView` → same name in UI kit; `LabeledContent` → same name.
  `dismiss()` → `LocalDismiss.current()`.
- **Text.** `Text("literal")`, `Button("literal")`, `Label("literal", …)`,
  `.navigationTitle("literal")`, `Toggle("literal")` etc. → `AppLocalization.string(…)`
  (§9). `Text(variable)`/`Text(verbatim:)` → not localized. Inline Markdown in
  literals → `inlineMarkdown(AppLocalization.string(…))` (`AnnotatedString`).
  `.scaledFont(f)` on `Text` → `style = f.textStyle`; on a container →
  `ProvideTextStyle(f.textStyle) { }` (`ScaledFont.textStyle` is a `@Composable` getter); `.font(.system(size:weight:))` →
  `TextStyle(fontSize = x.sp, fontWeight = …)`; `.lineLimit(n)` → `maxLines = n,
  overflow = TextOverflow.Ellipsis`; `.multilineTextAlignment(.center)` → `textAlign`;
  `.foregroundStyle(c)` → `color = c` / `CompositionLocalProvider(LocalContentColor provides c)`;
  `.textSelection(.enabled)` → `SelectionContainer`; `AttributedString`/
  `NSAttributedString` → `AnnotatedString` (`SpanStyle`, `ParagraphStyle`,
  `LinkAnnotation`).
- **Images and symbols.** `Image(systemName: n)` → `Image(systemName = n)` (UI kit;
  Material Symbols via `views/Symbols.kt`; every literal name must be mapped — a JVM
  test checks). `.symbolVariant(.fill)` → `systemName = "<name>.fill"`. `.imageScale`
  → size. `AsyncImage` → Coil `AsyncImage`. `Image(uiImage:)` → `Image(bitmap.asImageBitmap())`.
- **Colors and materials.** `.ultraThinMaterial`/`.regularMaterial`/`.bar`/glass →
  `Modifier.glass(shape)` / `conduitGlassControl(cornerRadius)` (surface 78% + 1dp
  white 16% hairline, upstream's pre-iOS 26 fallback); gradients → `Brush`.
- **Animation.** `withAnimation(.x) { state = … }` → assign state; animate the reader
  with `animate*AsState`/`AnimatedVisibility`/`AnimatedContent` using
  `ConduitMotion` specs (`.easeInOut(duration: d)` → `tween((d*1000).toInt(), easing = FastOutSlowInEasing)`,
  `.spring(response:dampingFraction:)` → `spring(dampingRatio, stiffness = (2π/response)²)`);
  respect `LocalAccessibilityReduceMotion` where upstream does. `.transition` →
  `AnimatedVisibility` enter/exit; `matchedGeometryEffect` → crossfade `// android:`.
- **Gestures.** `onTapGesture` → `clickable`/`pointerInput { detectTapGestures }`;
  `DragGesture` → `pointerInput { detectDragGestures }`; `onLongPressGesture` →
  `combinedClickable(onLongClick)`.
- **Accessibility.** `.accessibilityLabel(l)` → `Modifier.semantics { contentDescription = l }`;
  `.accessibilityHidden(true)` → `clearAndSetSemantics { }`;
  `.accessibilityElement(children: .combine)` → `semantics(mergeDescendants = true) { }`;
  `.accessibilityAddTraits(.isButton)` → `role = Role.Button`; `.isHeader` →
  `heading()`; `.accessibilityValue` → `stateDescription`; `.accessibilityAction` →
  `customActions`; `.accessibilityIdentifier(id)` → `Modifier.testTag(id)` (root sets
  `testTagsAsResourceId = true`; the smoke test finds views by these ids).
- **UIKit views** (`UIViewRepresentable`): port the *policy* (what the view decides);
  use Compose equivalents for mechanics (`UITextView` composer → `BasicTextField`;
  selectable text → `SelectionContainer`; `UIScrollView` → `LazyListState`/scroll
  state; `WKWebView` → `AndroidView(WebView)`), each with an `// android:` note.

## 9. Localization

- Every user-visible literal goes through `AppLocalization.string(key, args…)` exactly
  as the Swift code or SwiftUI literal would be keyed in `Localizable.xcstrings`.
  Interpolations become format specifiers in the key, typed the way Xcode extracts
  them: `String`/other → `%@`, `Int` → `%lld`, `Double` → `%lf`, `Float` → `%f`:
  `AppLocalization.string("Ready · \(shortened)")` → `AppLocalization.string("Ready · %@", shortened)`;
  `Text("\(count) chats")` → `AppLocalization.string("%lld chats", count)`.
  Check the key exists in the catalog (`grep -F '"<key>"' .upstream/hermes-conduit/Conduit/Localizable.xcstrings`);
  if it doesn't, the key is wrong.
- `table:` / `language:` arguments are named: `AppLocalization.string("X", table = "InfoPlist")`.
- Strings that upstream does not localize (logs, identifiers, `Text(verbatim:)`) stay
  plain. Android-only copy (updater) uses English keys; lookup falls back to the key.

## 10. Skeleton phase rules

The skeleton gives every Core file its declaration surface so body batches compile
against each other:
- Declare every non-private type, typealias, nested type, enum case, stored property
  (with its initializer), computed property and function signature visible outside
  the file; bodies, getters and initializers that need logic are `TODO("port")`.
  Keep `// MARK: - <Title>` comments as they are.
- Views: the entry composable signature and `object <ViewName>` with non-private nested
  types; the body is `TODO("port")`.
- AppState: `AppState.kt` with every stored property Core uses, its observers, every
  nested type and the full `init`; each section file with **every** Core member
  signature (private ones as `internal`) and the `// later:` lines for the rest (§13).
- Files of ≤ 200 Swift lines are ported completely during the skeleton pass; body
  batches list only larger files.
- A skeleton batch is done when
  `./gradlew :app:compileDebugKotlin 2>&1 | tee build/compile.log` followed by
  `scripts/port-check.py --errors build/compile.log` prints nothing: the only compiler
  errors left are unresolved references to names another unfinished Core batch
  declares. A name declared in a later file is always an error: apply §13.
- `scripts/swift-decls.py <file>` prints a Swift file's declarations with line numbers
  (bodies elided); use it instead of reading whole files when writing a skeleton.
- Body batches may change a skeleton signature only to fix a skeleton mistake, and then
  fix every caller in the same commit.

## 11. Tests

- JUnit 4 + `kotlin.test` + `kotlinx-coroutines-test`, no Robolectric. Android
  framework calls return defaults in unit tests (`isReturnDefaultValues = true`), so
  tests cover pure logic; code under test takes seams (`UserDefaults.scratch()`,
  `KeychainHelper.useBackendForTesting`, injected clocks) rather than touching
  `appContext`.
- Upstream test `ConduitTests/XTests.swift` → `app/src/test/kotlin/app/conduit/XTests.kt`,
  class and test names verbatim (`func testFooBar()` → `@Test fun testFooBar()`);
  `XCTAssertEqual(a, b)` → `assertEquals(a, b)` (upstream's argument order);
  `XCTAssertTrue/False/Nil/NotNil` → `assertTrue/assertFalse/assertNull/assertNotNull`; `XCTAssertThrowsError` → `assertFailsWith`; async tests →
  `runTest { }`. Header `// Port of: ConduitTests/XTests.swift @6ddae94`.
- `ConduitTests/XTests+Y.swift` (an `extension XTests`) → `XTestsY.kt` with
  `class XTestsY`; helpers it uses from `XTests` become `internal` top-level functions
  in `XTests.kt`, and `XTestsY` repeats `XTests`'s `@Before`/`@After`.
  `ConduitTests/Fixtures/` → `app/src/test/resources/fixtures/`, read with
  `javaClass.getResource("/fixtures/<name>")`.
- A batch ports the upstream tests named in its plan entry; skipped test cases get a
  one-line reason comment (`// skipped: UIKit text layout`). Cases that need UIKit,
  WebKit, JavaScriptCore or another iOS runtime are skipped; so are cases whose subject
  is later-owned (§13).
- A case that fails only because it reaches another batch's `TODO("port")` gets
  `@Ignore("needs <batch>")`. A batch's last step removes the ignores naming it
  (`grep -rn '"needs <batch>"' app/src/test`) and makes those cases pass.
- Tests that construct `AppState` call `ConduitTestEnvironment.install()` in `@Before`
  and `ConduitTestEnvironment.uninstall()` in `@After` (Main dispatcher, scratch
  `UserDefaults.standard`, in-memory keychain, temp `FileManager` root).
- Network stubs: an upstream `URLProtocol` subclass ports to `foundation/net`'s
  `URLProtocol`, its companion object implements `URLProtocolClass`, and the test
  installs it with `URLSessionConfiguration.protocolClasses = listOf(Stub)`.
  `LoopbackHTTPServer` → `mockwebserver3.MockWebServer`.

## 12. Per-file checklist

1. Header line present; package `app.conduit`; path per §1.
2. Every Swift declaration has a Kotlin counterpart, in the same order, same name.
3. No `TODO("port")` left (body batches); `// later:` markers only per §13.
4. Literals localized per §9; symbols mapped; accessibility modifiers carried over.
5. Catch-alls rethrow `CancellationException`; no main-thread I/O.
6. `./gradlew :app:compileDebugKotlin` and the batch's tests pass.

## 13. Core and later sub-projects

Spec §4 assigns upstream files to sub-projects; `scripts/portmap.py owner <SwiftName>`
prints where a name is declared and who owns that file (a Core batch or
`later:<sub-project>`). Core compiles and runs with no later sub-project present.

- **Ownership is per declaration, not per file or heading.** A member, type, field or
  case is later-owned when it exists for a later feature: voice, live calls, wake
  phrase, read-aloud (`voice`); CarPlay, Watch (`wear-auto`); share drafts, widgets,
  shortcuts, screen questions (`widgets-shortcuts-share`); push and notification routing
  (`notifications`); cron and scheduled jobs, kanban, bot management, group rooms,
  artifacts, rich cards, pulse, workspace (`bots-tasks-jobs`); health, phone, location
  (`phone-data`). Everything else is Core wherever it sits: an `AppState` member under
  `// MARK: Shared drafts` that the chat list reads is Core. When in doubt, Core.
- **Core never names a later-owned declaration.** Where it would:
  - a stored property, field, parameter, enum case or nested type whose type is
    later-owned is omitted, leaving `// later:<sub>: <name>` in its place;
  - a statement that calls or reads later-owned code is commented out under
    `// later:<sub>`. Where an expression needs the value, use the feature-absent value
    (no voice surface active, no room open, no share draft, no cron jobs: `false`,
    `null`, empty) and keep the original as a comment:
    `get() = isSceneActive // later:voice,wear-auto: || isCarPlayVoiceSurfaceActive || …`;
  - UI that shows a later feature (button, row, menu item, sheet, settings link) is not
    rendered, with `// later:<sub>` where it would be.
- **Later-owned members of a Core file** are not declared. One
  `// later:<sub>: name1, name2, …` line at their position lists them, so the
  sub-project finds its insertion points with `grep -rn '// later:<sub>'`.
- **Leaf exceptions.** A pure declaration in a later file that Core needs for a
  non-feature value (a constant, a pure function, a plain model) is ported in Core, in
  the Kotlin file mirroring that later file. The file holds only those declarations and
  ends with `// later:<sub>: rest of <File>.swift`; `upstream/port-map.json` lists it
  under `partial` with its Core line ranges. Moved into Core outright:
  `Services/BotModeModels.swift` (the chat list's bot-ownership rules need it).
  Partial: `Services/BotManagement.swift` lines 1–31 (`enum BotManagement` constants);
  `Models/CapabilityCatalogModels.swift` lines 131–201 (`CapabilityToolsetOrdering`,
  which names a step's tool) and 500–560 (`AgentPluginRow`, read by the notifier plugin
  status). `Models/CronScheduleText.swift` stays Core (the usage report parses dates
  with it); `Models/Kanban*.swift` and `Models/CronJobDraft.swift` are `bots-tasks-jobs`.
- Never fake a later feature: no stub types, placeholder screens or empty
  implementations of later protocols.
