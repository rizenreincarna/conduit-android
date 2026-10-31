# Conduit for Android

Native Android port of [Hermes Conduit](https://github.com/kaishi00/hermes-conduit) — the
SwiftUI client for a self-hosted [Hermes Agent](https://github.com/NousResearch/hermes-agent)
dashboard. Kotlin + Jetpack Compose, no Compose-from-web, no Play-services dependency.

Pinned upstream: `kaishi00/hermes-conduit` at tag `ios/v2.2.1-build-244`
(`6ddae94189dd21becf2e2892038154c19d47393f`); companion notifier at `9eac857`.
See `upstream/upstream.json` for the machine-readable pin.

## Status

Work is organised into seven sub-projects (`docs/superpowers/specs/`). **Core is in progress**;
the other six are not started.

Ported so far — the pure-Kotlin Core surface, line-for-line per `docs/PORTING.md`:

| Area | Files |
|---|---|
| `foundation/` | Errors, Serialization, URLs, UserDefaults |
| `services/` | AppLanguage, ConnectionURLPolicy, DashboardPath, DashboardSignInRedirect, DataURLLimits |
| `shared/` | LogRedaction |
| app shell | ConduitApplication, MainActivity, manifest, theme, 14 locale bundles, network-security config |

Unit tests cover `ConnectionURLPolicy` origins, the foundation helpers and `LogRedaction`.

Not yet ported: the dashboard client (WebSocket `/api/ws` + REST), chat UI, sessions, profiles,
settings, offline cache, updater, then Bots/Tasks/Jobs, Voice, Notifications, phone data,
widgets, Wear OS and Android Auto.

This is an unfinished port. It is not an app you can install and use yet.

## Layout

```
app/src/main/kotlin/app/conduit/   ported Kotlin (see docs/PORTING.md for path rules)
app/src/main/assets/l10n/          locale strings carried over from upstream
app/src/test/kotlin/               ported unit tests
docs/PORTING.md                    how every file is ported (rules, markers, naming)
docs/superpowers/specs/            Core design spec
docs/superpowers/plans/            the 55-batch port plan (JSON, drives the helper scripts)
upstream/                          upstream pin + Swift→Kotlin path map
scripts/                           portmap, swift-decls, core-batches, upstream helpers
```

## Build

The Gradle wrapper is committed. `local.properties` (gitignored) must point at your SDK:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
```

JDK 21 (toolchain pin in `mise.toml`), AGP/Kotlin versions in `gradle/version.gradle.kts`.

## Attribution

Conduit is MIT-licensed. This port is a derivative work: the Swift sources are read from a local
checkout of the pinned upstream (`.upstream/`, not committed) and translated file by file, so the
structure, names and behaviour follow `kaishi00/hermes-conduit`. Upstream copyright is retained in
`LICENSE`; changes here are a translation to Kotlin, not a reimplementation.

Not affiliated with or endorsed by Nous Research or the Conduit author.
