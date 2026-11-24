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

## Installing on a phone

`minSdk = 26`, so any phone on Android 8.0 or newer can run it. The release APK is ~1.9 MB.

### Get the APK

`dist/` is not committed, so either use the build already there or make one:

```sh
export JAVA_HOME="C:/Users/EmilShain/Code/spliit-workspace/.tooling/jdk21"
export ANDROID_HOME="C:/Users/EmilShain/Code/spliit-workspace/.tooling/sdk"

./gradlew :app:assembleRelease

BT="$ANDROID_HOME/build-tools/35.0.0"
KS="C:/Users/EmilShain/Code/spliit-workspace/.tooling/keys/spliit-test.jks"

mkdir -p dist
"$BT/zipalign.exe" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk dist/aligned.apk
"$BT/apksigner.bat" sign --ks "$KS" --ks-key-alias spliit \
  --ks-pass pass:spliitdev --key-pass pass:spliitdev \
  --out dist/spliit-0.1.0-release.apk dist/aligned.apk
rm dist/aligned.apk

# what is in it: package app.spliit, minSdk 26, targetSdk 35
"$BT/aapt2.exe" dump badging dist/spliit-0.1.0-release.apk | head -3
```

The `.exe` / `.bat` suffixes are not optional in Git Bash: `apksigner` on its own is not found, and
an unsigned APK will not install.

That is `dist/spliit-0.1.0-release.apk`, signed with the local test key at
`../.tooling/keys/spliit-test.jks`. It is not a Play-ready release: the key is a throwaway, so
keep that keystore if you want later builds to upgrade over this one instead of having to be
uninstalled first.

### Install it

With the phone plugged in and USB debugging on (Developer options → USB debugging):

```sh
adb devices                  # confirm the phone is listed and authorised
adb install -r dist/spliit-0.1.0-release.apk
```

Without adb — copy the file over and tap it:

```sh
adb push dist/spliit-0.1.0-release.apk /sdcard/Download/   # or USB file transfer, or email it
```

Then open **Files → Downloads → spliit-0.1.0-release.apk**, allow "install unknown apps" for the
Files app when prompted, and tap **Install**. Play Protect may ask to scan the app first; that is
normal for a sideloaded APK.

### Two things to know

- If a debug build is already installed on that phone, uninstall it first. Debug and release are
  signed with different keys and Android refuses the upgrade (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- The release build only talks to `https://spliit.app`. Plain-`http://` local instances are
  reachable from the debug build alone (see [`docs/LOCAL_TESTING.md`](docs/LOCAL_TESTING.md)), so
  use `spliit-0.1.0-debug.apk` for that.

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
