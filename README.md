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

Working on the code? Read [AGENTS.md](AGENTS.md) first — it records the UI conventions,
including the rule that every loading page draws a skeleton rather than a spinner.

### Get the APK

The quickest way is the local script, which builds, aligns and signs it in one go:

```sh
bash run-local.sh apk         # dist/spliit-0.1.0-release.apk, for spliit.app groups
bash run-local.sh apk debug   # dist/spliit-0.1.0-debug.apk, for a local http server
```

To do it without a terminal, double-click `make-apk.cmd` (or run `make-apk.cmd debug` for the debug build).

Or do it by hand:

`dist/` is not committed, so the build has to be made once per machine:

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

## Run it on the emulator

The whole loop is scripted in the git-ignored `run-local.sh` — `bash run-local.sh` reuses a
fake server and emulator if they are already up, otherwise starts them, then rebuilds,
replaces the installed APK and launches the app. The manual equivalent follows; it assumes
this workspace's toolchain (`../.tooling`), an AVD named `spliit_api30`, and a fake Spliit
instance built from the test fixtures. It starts the emulator with a window so the screens
can be used by hand; details and gotchas are in [`docs/LOCAL_TESTING.md`](docs/LOCAL_TESTING.md).

```sh
export JAVA_HOME="C:/Users/EmilShain/Code/spliit-workspace/.tooling/jdk21"
export ANDROID_HOME="C:/Users/EmilShain/Code/spliit-workspace/.tooling/sdk"
export MSYS_NO_PATHCONV=1
ADB="$ANDROID_HOME/platform-tools/adb.exe"
```

**1. Emulator** (leave running in its own shell):

```sh
"$ANDROID_HOME/emulator/emulator.exe" -avd spliit_api30 -no-audio -no-boot-anim \
  -no-snapshot -gpu swiftshader_indirect -port 5554

"$ADB" -s emulator-5554 wait-for-device
"$ADB" -s emulator-5554 shell getprop sys.boot_completed   # 1 when ready
```

**2. Fake backend** (its own shell; use Node, not Bun):

```sh
node tools/fake-spliit/server.mjs     # http://0.0.0.0:8790
```

**3. Build, install, launch** — the loop to repeat after every code change:

```sh
./gradlew :app:assembleDebug
"$ADB" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s emulator-5554 shell am force-stop app.spliit
"$ADB" -s emulator-5554 shell am start -n app.spliit/.MainActivity
```

On a first launch, add the group by link with
`http://10.0.2.2:8790/groups/sh__QL0eGqn-fw98LhAtW`.

**4. Look at the screen:**

```sh
"$ADB" -s emulator-5554 exec-out screencap -p > shot.png
```

**5. Tap / type on it** (bounds come from the UI dump):

```sh
"$ADB" -s emulator-5554 shell uiautomator dump /sdcard/ui.xml
"$ADB" -s emulator-5554 shell cat /sdcard/ui.xml
"$ADB" -s emulator-5554 shell input tap <x> <y>
"$ADB" -s emulator-5554 shell input text "hello"
```

If a screen keeps failing against the fake instance, the OkHttp pool is holding a dead
connection from a server restart: `am force-stop app.spliit` and relaunch.

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
