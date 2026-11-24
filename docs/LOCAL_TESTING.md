# Testing on an emulator

Everything here runs from the workspace without a system-wide SDK install. The screens are
otherwise only covered by Robolectric tests, which render Compose on the JVM; this is how they get
looked at on a real Android runtime.

Verified on **API 30 (Android 11)** — the lowest version the port promises — with an x86_64
Google APIs image on a Pixel 5 profile. Nothing here is API-30 specific.

## 1. Emulator

The emulator package and system image are not part of the default toolchain install; add them
once:

```sh
export JAVA_HOME="C:/Users/EmilShain/Code/spliit-workspace/.tooling/jdk21"
export ANDROID_HOME="C:/Users/EmilShain/Code/spliit-workspace/.tooling/sdk"

yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" \
  "emulator" "system-images;android-30;google_apis;x86_64"

echo "no" | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat" create avd \
  -n spliit_api30 -k "system-images;android-30;google_apis;x86_64" -d pixel_5
```

`-accel-check` should report `WHPX ... is installed and usable` on Windows; without it x86_64
images will not boot in reasonable time.

Boot it (`-no-window` is fine — `adb exec-out screencap` still captures the screen):

```sh
"$ANDROID_HOME/emulator/emulator.exe" -avd spliit_api30 -no-window -no-audio \
  -no-boot-anim -no-snapshot -gpu swiftshader_indirect -port 5554

# ready when this prints 1
adb -s emulator-5554 shell getprop sys.boot_completed
```

## 2. A fake Spliit instance

The app accepts any instance address, so the screens can be filled from the fixtures the unit
tests already use instead of creating throwaway groups on `spliit.app`:

```sh
node tools/fake-spliit/server.mjs      # http://0.0.0.0:8790, Node 18+
```

`tools/fake-spliit/server.mjs` serves `app/src/test/resources/fixtures/*.json` verbatim as tRPC
envelopes for `groups.get`, `groups.list`, `groups.expenses.list` (honouring the search `filter`),
`groups.balances.list`, `groups.stats.overview` / `groups.stats.get`, `groups.activities.list` and
`categories.list`. Anything else answers `404 NOT_FOUND`, the way an instance that predates a
procedure does. The fixture set is the "Weekend in Lisbon" group with three participants.

Two things that cost time the first run:

- Use Node, not Bun: Bun's HTTP server dropped responses to OkHttp keep-alive connections, which
  surfaces in the app as `unexpected end of stream on http://10.0.2.2:8790/...`.
- Restarting the server while the app is open leaves OkHttp holding a dead pooled connection; the
  next screen fails the same way. Force-stop the app (`adb shell am force-stop app.spliit`) or
  wait for OkHttp to evict it.

## 3. Install and point it at the instance

The debug build allows cleartext (`app/src/debug/AndroidManifest.xml`), so it can reach the host
over plain http; the release build cannot. The emulator reaches the host as `10.0.2.2`.

```sh
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n app.spliit/.MainActivity
```

Then, in the app: **Add by link** and paste

```
http://10.0.2.2:8790/groups/sh__QL0eGqn-fw98LhAtW
```

(the group ID in `groups-get.json`). Typing it through `adb shell input text` loses leading
characters if the whole string is sent at once; send it a character at a time, and remember
`-no-window` shells need `MSYS_NO_PATHCONV=1` so `/groups/...` is not rewritten to a Windows path.

## 4. Driving and capturing

Compose exposes little to `uiautomator`, so take a dump, read the bounds, then tap:

```sh
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml
adb -s emulator-5554 shell cat /sdcard/ui.xml
adb -s emulator-5554 shell input tap <x> <y>
adb -s emulator-5554 exec-out screencap -p > shot.png
```

The tab bar sits at the top of the group screen; the four tabs are Expenses, Balances, Totals and
Information, and search is the magnifier to the left of the add button. The activity log is opened
from the Activity card in the Information tab.

## 5. Screens that were checked this way

Expenses (date buckets, payer lines), Totals (overview stats plus the reconciling category sweep),
Information, the Activity log, and Search (empty state and results). This run is what turned up
the wrapping `Information` tab label and the search empty state sitting behind the keyboard, both
fixed since.

Not covered by this setup: gestures (swipe-to-delete, pull-to-refresh, paging as you scroll),
other densities or locales, and anything that needs a populated live instance.
