# Conduit for Android — program and Core design

Date: 2026-11-01. Upstream: `kaishi00/hermes-conduit` at tag `ios/v2.2.1-build-244`
(`6ddae94`), notifier plugin `kaishi00/hermes-conduit-notifier` at `9eac857`.

## 1. Goal

A fully native Android port of Conduit (the SwiftUI client for the Hermes Agent
dashboard), 1:1 in behavior and look ("same look, Android feel"), plus two ways to
stay current: tooling that ports new upstream releases, and an in-app updater that
installs new APKs from this repo's GitHub Releases.

The upstream app is ~252K lines of Swift (+186K lines of tests). The port runs as
seven sub-projects, each with its own plan, in this order:

1. **Core** — every sign-in method, chat (streaming, Markdown, tools, approvals,
   clarify, attachments), sessions, profiles, settings, offline cache, the updater
   and the upstream-sync tooling. This document.
2. **Bots, Tasks, Jobs** — bots, Kanban board, scheduled jobs, capabilities,
   artifacts/workspace files, rich cards, Pulse, group rooms.
3. **Voice** — dictation, classic voice, then the live providers (Gemini, GPT, Grok,
   Local, Qwen) and Hermes calls (Telecom).
4. **Notifications** — foreground-service connection + local notifications in place
   of APNs, ongoing notifications for running turns (Live Activities).
5. **Phone data** — location, calendar (CalendarContract), Health Connect, contacts.
6. **Widgets, shortcuts, share** — Glance widgets, app shortcuts, share target,
   ask-about-screen.
7. **Wear OS and Android Auto**.

Voice moved ahead of Notifications: the user put Voice and Bots/Tasks/Jobs first.

## 2. Updates

### In-app updater (Core)
- Version: `versionName = <upstream marketing>-a<rev>` (`2.2.1-a1`),
  `versionCode = upstreamBuild * 100 + rev` (`24401`). Both live in
  `gradle.properties` (`conduit.upstreamVersion`, `conduit.upstreamBuild`,
  `conduit.androidRevision`) and `conduit.releaseRepo=<owner>/<repo>`.
- Pushing tag `v2.2.1-a1` runs `.github/workflows/release.yml`: builds the release
  APK signed with the key from repository secrets, computes SHA-256, publishes a
  GitHub Release with the APK, `update.json`
  (`{versionCode, versionName, apkUrl, sha256, notes, upstream:{tag, sha}}`) and notes.
- The app fetches `https://github.com/<repo>/releases/latest/download/update.json`
  once a day on launch (Settings › About › "Check automatically", default on) and on
  "Check for updates". Newer `versionCode` → sheet with notes → download with
  progress → SHA-256 check → `PackageInstaller` session. Missing "Install unknown
  apps" permission → explain and open the system setting, then resume.
- Release signing key is generated once; the user keeps an offline backup (losing it
  means users must uninstall to update). Secrets: `SIGNING_KEYSTORE_B64`,
  `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

### Upstream sync (Core)
- `upstream/upstream.json` — the upstream tag + sha the port matches.
- `upstream/port-map.json` — exceptions only: Swift file → Kotlin file(s) when not
  the default path mapping, `skip` with a reason, `later:<sub-project>`.
- `scripts/upstream.py`:
  - `status` — fetch upstream; list releases newer than the lock.
  - `report [--to <tag>]` — write `upstream/PENDING.md`: per upstream PR between
    the lock and the target, a checklist of changed Swift files with their Kotlin
    counterparts (via default mapping + port-map; for the big files, the changed
    `// MARK:` sections and their Kotlin section files), plus string-catalog changes.
  - `strings` — regenerate translations (§3.5) from the catalogs.
  - `mark-synced <tag>` — update the lock.
- `.claude/commands/port-upstream.md` — the `/port-upstream` Claude Code command:
  run `report`, port each PENDING item, run checks, bump `versionName`, mark synced,
  open a PR.
- `.github/workflows/upstream-check.yml` — weekly; opens or updates one issue when
  upstream has a newer release tag.

## 3. Architecture

### 3.1 Shape
- One Gradle module `app`, single Activity, Jetpack Compose, Navigation 3 back stacks.
  minSdk 33 (mirrors the iOS 17 floor: per-app language, notification permission,
  photo picker, AGSL), compileSdk/targetSdk 37.
- `applicationId = app.conduit.android`, namespace `app.conduit`. Fixed now so it
  never changes after the first published APK.
- No ViewModels, no DI framework. `AppState` stays **one Kotlin class** holding all
  stored state, app-scoped in `ConduitApplication` (upstream's root `@StateObject`).
  Views get it through `LocalAppState`.
- Every ported file starts with `// Port of: <upstream path> @6ddae94`.

### 3.2 Source layout and the single package
Folders mirror upstream, lowercased: `Conduit/Views/V2/V2Shell.swift` →
`app/src/main/kotlin/app/conduit/views/v2/V2Shell.kt`; `Shared/X.swift` →
`.../app/conduit/shared/X.kt`.

All ported code declares **one package, `app.conduit`**, whatever its folder. Swift
compiles the app as one module where every type sees every other without imports;
one Kotlin package keeps that property, so a port never needs imports between ported
files and names stay as unique as they are upstream. (Kotlin only warns when the
package doesn't match the folder.) `private` at file level means the same in both.

AppState is split for size: `services/AppState.kt` holds the class, all stored
properties and `init`; each `// MARK:` section of `AppState.swift` becomes
`services/appstate/AppState_<Section>.kt` (PascalCase MARK title) holding that
section's methods and computed properties as extension members; each
`AppState+X.swift` becomes `services/AppStateX.kt`. Swift `private` members reached
across those files become `internal`. One package means one JVM facade class per file
name, so Kotlin file names stay unique module-wide, as upstream's are within its target.

### 3.3 Translation rules (summary — full table in `docs/PORTING.md`)
| Swift | Kotlin |
|---|---|
| `@Published var x` | `var x by mutableStateOf(...)` (plain `var` stays plain) |
| `@MainActor` | main thread (`Dispatchers.Main.immediate`), `AppState.scope` |
| `Task {}` / `Task.detached {}` | `scope.launch {}` / `launch(Dispatchers.Default)` |
| `async`/`await` | `suspend` |
| struct `Codable` | `@Serializable data class` (optional → `= null`) |
| enum with payloads | `sealed interface` |
| `[String: Any]` (JSONSerialization) | `Map<String, Any?>` + `JSONSerialization` shim and typed getters mirroring `as? Int` bridging |
| `Date` / `TimeInterval` / `UUID` / `URL` | `java.time.Instant` / `Double` seconds / `java.util.UUID` / `java.net.URI` |
| `UserDefaults.standard` | `UserDefaults` shim over SharedPreferences, same keys |
| Keychain (`KeychainHelper`) | same API over Android Keystore AES-GCM, same account names |
| Application Support / Caches | `filesDir` / `cacheDir` |
| `os.Logger` | `Logger` shim over `android.util.Log` |
| `.sheet` / `.alert` / `NavigationStack` | `ModalBottomSheet` / `AlertDialog` / Navigation 3 |
| SF Symbols | Material Symbols, one mapping table (`views/Symbols.kt`) |
| Liquid Glass | upstream's pre-iOS 26 fallback: surface 78% + 1dp white 16% hairline |
| Metal shaders | AGSL `RuntimeShader` |

The foundation shims (`app/conduit/foundation/`) exist so call sites translate line
for line; they are thin and carry no behavior of their own.

### 3.4 Concurrency and staleness
Upstream's fences port as-is: `activeClientEpoch` checks in every client callback,
`reconciliationToken`, `HermesClient.connectGeneration`, socket identity guards,
`bufferIfReconciling` during resume, `LiveTurnProjection` as its own state holder for
streaming text. WebSocket frames ≥16 KB decode off the main thread, as upstream.

### 3.5 Localization
Upstream routes every UI string through `AppLocalization.string("English key")`
(5,072 call sites, keys are the English source). The port keeps that API exactly.
`scripts/upstream.py strings` converts `Localizable.xcstrings` into
`app/src/main/assets/l10n/<lang>.json` (14 languages; Swift format specifiers
rewritten for `String.format`; plural variations kept and chosen with
`android.icu.text.PluralRules`). Lookup falls back to the English key, as upstream.
The in-app language picker sets `LocaleManager.setApplicationLocales`; system strings
(app name) and `locales_config.xml` come from the same script. This replaces the
earlier "strings.xml per language" idea: same coverage, call sites unchanged, and an
upstream catalog update is one script run.

### 3.6 Security and errors
- Credentials, cookies, OAuth tokens, Cloudflare tokens, custom headers: encrypted
  with a Keystore AES-GCM key (non-exportable), stored in a private prefs file.
  Biometric-gated credentials use `BiometricPrompt` where upstream asks for Face ID.
- Cleartext: Android's network security config can't express "private LAN and
  Tailscale only", so the manifest permits cleartext and `ConnectionURLPolicy`
  (ported unchanged: HTTPS, or HTTP for localhost, private LAN, `100.64.0.0/10`,
  `*.ts.net`) gates every URL before use, as iOS ATS + the same policy do upstream.
- WebView (ticket bridge, OAuth fallback) keeps upstream's navigation policy
  (main-frame vs subframe rules).
- Logs go through the ported `LogRedaction`.
- User-facing errors keep upstream's `UserFacingError`/`ConnectionFailure` copy.

## 4. Core scope

Core ports every file under `Conduit/Models`, `Conduit/Services`, `Conduit/Views`,
`Conduit/Theme.swift`, `Conduit/ConduitApp.swift` and `Shared/LogRedaction.swift`,
`Shared/WatchVoice/CustomAccentColor.swift`, **except** the lists below. Inside
`AppState.swift`, sections owned by a later sub-project are not ported in Core;
calls into them from Core code become `// later:<sub-project>` markers, and their
stored properties are added when that sub-project lands. UI entry points into later
sub-projects (tabs, rows, buttons) are hidden in Core behind the same markers.

**Later: bots-tasks-jobs** — Services: `Bot*`, `Capabilit*` (store + toggle
writes), `Artifacts`, `AppState+Artifacts`, `Workspace*`, `AppState+Workspace`,
`Pulse*`, `AppState+Pulse`, `GroupChatModels`, `GroupRoom*`, `Kanban*`,
`ChatCardsModel`, `AppState+ChatCards`; `Conduit/Cards/*`. Views: `Bot*`,
`Capabilities/*`, `CapabilitiesView`, `GroupChatView`, `GroupRoomScrollFollow`,
`Components/GroupRoomComposer`, `Kanban/*`, `KanbanViews`, `V2/CronJobEditorSheet`,
`V2/V2ArtifactsViews`, `V2/V2BotsView`, `V2/V2PulsePage`, `V2/V2TasksView`,
`V2/V2WorkspaceViews`, `PulseSettingsViews`. AppState sections: Bot Mode (both),
Bot management, Group Chats, Capabilities (both), Cron, Scheduled jobs.
(Models are all ported in Core; they are small and AppState's types need them.)

**Later: voice** — `Conduit/Voice/**`, `Shared/GeminiLive|GrokLive|LocalLive/**`,
`Shared/WatchVoice/*` (except `CustomAccentColor`); Services `AppState+HermesCalls`,
`AppState+LiveVoiceConsent`, `AppState+SpokenApprovals`, `AppState+VoiceHostTools`,
`AppState+VoiceMessage`, `AppState+WakePhrase`, `VoiceReadSeal`; Views
`DictationSettingsSection`, `*Live*Views`, `LiveVoice*`, `HermesCallSettingsViews`,
`LiquidOrb/*`, `MinimisedLiveVoiceBar`, `ReadAloudPlayerBar`, `SpeechServer*`,
`V2/ChatVoiceButtonAction`, `V2/V2VoiceButton`, `Voice*` views,
`Components/ComposerDictationStopButton`, `Components/UndeliveredDictationNotice`,
`Components/VoiceInputLevelMeter`, `Components/VoiceMessageBar`. AppState sections:
Gemini/GPT/Grok/Local Live, GPT-Live billing, Wake phrase, Voice history, Finding
chats from a call, Waiting call, Minimised live voice, Live voice attached to a chat.

**Later: notifications** — `PushNotificationService`, `NotificationCategories`,
`NotificationKeyRotation`, `LiveTurnActivities`, `LiveActivityUpdateThrottle`,
`ReplySourceNotifications`, `CronNotifications`, `AppState+CronNotifications`,
`Shared/NotificationE2E`, `Shared/Notifications/*`, `ReplySourcesSettingsSection`.
(Notifier plugin status/update and synced preferences are Core: setup and Settings
use them.)

**Later: phone-data** — `Conduit/Phone|Location|Health/*`, `Shared/Location/*`,
`AppState+HealthSync|Location|PhoneSync`, `Health|Location|PhoneSettingsViews`.

**Later: widgets-shortcuts-share** — `Conduit/Intents|Shortcuts/*`,
`Shared/ShareDraft|Widgets/*`, `AppState+ShareDraft|ShortcutInbox|ShortcutSend|
WidgetLaunch|Widgets|ScreenQuestion`, `WidgetLaunchCoordinator`,
`WidgetSnapshotPublisher`, `ShortcutInboxSettingsViews`, `ScreenQuestionViews`,
`AskHermesPromptView`; AppState sections Shared drafts, Ask Hermes About Screen.

**Later: wear-auto** — `Conduit/Watch|CarPlay/*`, `ConduitWatch/**`,
`CarPlaySettingsSection`; AppState section Apple Watch voice.

**Skip (recorded in port-map with the reason)** — the legacy drawer shell
(`SidebarView`, `SidebarLayout`, `SidebarTab`, `LegacySettingsView`, the V2 on/off
preference: V2 is upstream's default and only maintained shell); test and screenshot
fixtures (`ScreenshotCallFixture`, `WidgetLaunchUITestFixture`,
`SelectionFixtureView`, `ConduitScreenshots/`); `MacTooltip` (Mac Catalyst);
`BackgroundTaskHold` becomes a no-op (Android doesn't suspend the process on
background the way iOS does); `MetricKitReports` is replaced by
`ApplicationExitInfo` feeding the same `CrashReportOffer`.

## 5. Porting method

Skeleton first, then bodies, so parallel work always compiles:

1. **Foundation (by hand)** — Gradle project, manifest, `ConduitApplication`,
   `MainActivity`, foundation shims, `Theme.kt`, `Symbols.kt`, l10n generator +
   `AppLocalization`, `docs/PORTING.md`, three exemplar ports (a model, a service, a
   view) that set the style.
2. **Models** — ported completely (leaf code).
3. **Skeleton** — every other Core file gets its full declaration surface (types,
   stored properties, signatures, composable parameters) with `TODO("port")` bodies;
   AppState gets every stored property its Core sections declare. Merge, then make the
   whole module compile.
4. **Bodies** — batches of ~3–5K Swift lines, one subagent per batch in its own git
   worktree, bottom-up (services → AppState sections → components → screens). Each
   batch must compile and pass its checks before merge; the main branch builds after
   every merge.
5. **Wire-up and run** — demo Hermes, emulator, fix until the flows in §6 pass.
6. **Updater, upstream tooling, release workflow.**

At most 3–4 concurrent agents (the gateway rate-limits more). Bulk translation uses
Sonnet; the concurrency-critical files (HermesClient, reconciliation, stream event
handling, resume) use Opus.

## 6. Testing

- **JVM unit tests** for pure logic, ported from upstream's own tests where they
  pin protocol or policy: URL policy, stream event parsing, resume policy, session
  references, connection failure classification, pinned order, Markdown block
  parsing, l10n formatting, updater version comparison and hash check.
- **Demo Hermes on Linux** — `scripts/demo-hermes.sh`, adapted from upstream's
  `scripts/app-store/start-demo-hermes.sh`: same pinned Hermes commit, LLM stub,
  seed data and notifier plugin, no `/etc/hosts` edit; the emulator reaches it at
  `http://10.0.2.2:9119` (private LAN → allowed by the URL policy).
- **Emulator smoke** — `scripts/smoke.sh` installs the debug APK on the `conduit`
  AVD and drives: sign in (password), chats list loads, open chat, send, streamed
  reply renders, approval prompt round-trip, switch profile, offline cache shows
  chats after killing the server, settings language switch. Screenshots go to
  `build/smoke/`.
- `./gradlew check` (unit tests + lint) runs in CI on every push.

## 7. Risks

- **Size.** Core alone is ~130K Swift lines. Skeleton-first keeps it compiling;
  what's left is whatever still contains `TODO("port")`.
- **iOS-only mechanics.** The chat scroll engine and selectable text are UIKit-bound;
  their policy ports 1:1 and their mechanics use Compose equivalents
  (`LazyListState`, `SelectionContainer`), noted with `// android:` comments.
- **Push.** APNs has no Android analog without Firebase; Core shows notifications
  only while the app runs; the Notifications sub-project adds the foreground service.
