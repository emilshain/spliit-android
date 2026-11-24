# spliit-android

A 1:1 functional and visual Android port of [spliit-ios](https://github.com/spliit-app/spliit-ios)
(SwiftUI), built with Kotlin + Jetpack Compose against the existing Spliit web backend
([spliit-app/spliit](https://github.com/spliit-app/spliit)) over its tRPC API.

## Compatibility

- `minSdk = 26` (Android 8.0) — verified target Android 11 / API 30 and older
- `targetSdk = 35`, `compileSdk = 35`
- Core library desugaring enabled for `java.time`
- No Android 12+ only APIs; the exact iOS colour palette is used on every version

## Reference checkouts

This project lives beside two read-only reference clones:

```
C:\Users\EmilShain\Code\spliit-workspace\
  ref-spliit-ios\    # SwiftUI reference (UI + logic source of truth)
  ref-spliit-web\    # Next.js backend (tRPC contract + Prisma schema)
  ref-spliit2go\    # unofficial Flutter/Dart Android client (independent port)
  spliit-android\    # this repository
```

`ref-spliit2go` is [sharneng/spliit2go](https://github.com/sharneng/spliit2go), an
existing unofficial Android client built in Flutter. It is read-only reference, not a
base: the brief here is a 1:1 port of the iOS app, so `ref-spliit-ios` stays the source
of truth for UI and logic. spliit2go is useful as a second opinion on the backend —
`lib/api/spliit_client.dart` (its own tRPC client), `lib/services/expense_shares.dart`,
`lib/services/balance_calculator.dart` and `lib/services/date_only.dart` are independent
implementations of the same maths, and its `docs/decisions/` records hard-won detail
about the API that the server source does not spell out.

## Toolchain

Everything builds from the workspace without a system-wide SDK install:

| Component | Version | Location |
|---|---|---|
| JDK | Temurin 21 | `../.tooling/jdk21` (via `org.gradle.java.home`) |
| Android SDK | platform 35, build-tools 35.0.0 | `../.tooling/sdk` (via `local.properties`) |
| Gradle | 8.13 (wrapper) | `./gradlew` |
| AGP / Kotlin | 8.9.2 / 2.1.21 | `build.gradle.kts` |

```sh
./gradlew :app:assembleDebug
```

`org.gradle.java.home` only applies to the build daemon, so a shell whose default
JDK is not 21 must also export the launcher's JDK:

```sh
export JAVA_HOME="C:/Users/EmilShain/Code/spliit-workspace/.tooling/jdk21"
export ANDROID_HOME="C:/Users/EmilShain/Code/spliit-workspace/.tooling/sdk"
```

`local.properties` is git-ignored; a fresh clone needs its own `sdk.dir`.

## Testing

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug -Dspliit.skipLive=true
```

`-Dspliit.skipLive=true` skips the tests that talk to the real `spliit.app`. The UI tests render
Compose on the JVM under Robolectric; to look at the screens on an emulator against a local fake
instance, see [`docs/LOCAL_TESTING.md`](docs/LOCAL_TESTING.md).

## Status

Phase 0 complete: workspace, reference clones, codebase audit (`docs/AUDIT.md`),
and a verified `assembleDebug` scaffold.
