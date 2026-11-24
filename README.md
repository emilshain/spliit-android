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
  spliit-android\    # this repository
```

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

## Status

Phase 0 complete: workspace, reference clones, codebase audit (`docs/AUDIT.md`),
and a verified `assembleDebug` scaffold.
