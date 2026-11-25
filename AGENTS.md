# Working in this repository

Read this before changing anything here. It records the conventions the app already follows, so
new work matches instead of drifting.

`spliit-android` is a Kotlin/Compose port of the iOS Spliit app, on Material 3 with an expressive
treatment. UI only: no accounts, no analytics, no backend of its own.

## Build and check

```sh
export JAVA_HOME="C:/Users/EmilShain/Code/spliit-workspace/.tooling/jdk21"
export ANDROID_HOME="C:/Users/EmilShain/Code/spliit-workspace/.tooling/sdk"

./gradlew.bat :app:compileDebugKotlin -Dspliit.skipLive=true
./gradlew.bat :app:testDebugUnitTest -Dspliit.skipLive=true
```

`-Dspliit.skipLive=true` keeps the suite off the live spliit.app instance.

For a device: `run-local.cmd` (release/debug build, install, launch on the emulator) and
`make-apk.cmd` (signed APK in `dist/`). Both wrap `run-local.sh`, which is git-ignored along with
`run-local.cmd` and `make-apk.cmd` because they carry this machine's tool paths.

## Loading states: skeletons, not spinners

**Every screen or tab that fetches must draw a skeleton of the page it is about to show, not a
spinner in the middle of an empty screen.** This applies to existing pages and to any page added
later. A spinner is only correct for something small happening inside a page that is already
drawn: a button that is saving, the footer that is paging a list, a row that is busy.

The primitives live in `app/src/main/java/app/spliit/ui/theme/Skeleton.kt`:

| Composable | Use it for |
| --- | --- |
| `SkeletonBox(modifier, shape)` | one block: a tile, an image, a bar |
| `SkeletonLine(widthFraction, height)` | a line of text, sized to the real line |
| `SkeletonCircle(size)` | a monogram, glyph or avatar |
| `SkeletonCard(height, shape)` | the surface a block of content sits on |
| `SkeletonRow(leadingSize, trailingWidth)` | a list row: leading block, two lines, amount |
| `SkeletonSection(width)` | a section header |
| `SkeletonListPage(rows, sections)` | the generic fallback: headers and rows under the tabs |

They shimmer on a shared 1.3s sweep and take their tint from `onSurface` at low alpha, so they
follow the theme into dark mode and inherit the tonal surfaces without a second palette.

Write the skeleton in the screen's own file, next to the real composition, and match its layout —
same paddings, same card shapes, same row heights. Then point the loading branch at it:

```kotlin
when {
    model.isLoading -> ThingSkeleton()
    model.failed -> EmptyState(...)
    else -> ThingContent(...)
}
```

The page-level skeletons already in place (`ExpenseListSkeleton`, `BalancesSkeleton`,
`TotalsSkeleton`, `ActivitySkeleton`, `SearchSkeleton`, `ReceiptSkeleton`, `GroupPageSkeleton`,
`SettingsSkeleton`) are the templates. If you add a page and cannot match its layout with the
primitives, extend `Skeleton.kt` rather than dropping back to `CircularProgressIndicator`.
`SkeletonListPage` exists so that no new page ever has an excuse to show a spinner for a load.

## Motion

`ui/theme/Motion.kt` holds the springs. Motion is SwiftUI's, not Material's timed curves:
`SpliitMotion.snappy()`, `bouncy()`, `smooth()` and `swiftSpring(response, dampingRatio)` are the
same presets SwiftUI names, expressed as stiffness = (2π / response)² with a damping ratio.
Colour and opacity use the critically damped ones; anything that moves, sizes or reshapes uses a
spatial spring. `Modifier.pressScale(...)` is the touch response.

## Elevation

Tone first, shadow second. Static containers separate by `surfaceContainer*` colour, never by a
drop shadow. Only things that genuinely float above the page take a shadow — the menu, the action
pills the add button unfolds, and the undo bar — using the numbers in `SpliitElevation`. The FAB
keeps Material's own shadow. Do not add `Modifier.shadow` to a card, row, bar or tab bar.

## Menus

Use `ExpressiveMenu` / `ExpressiveMenuItem` from `ui/theme/Menu.kt`; never a bare
`DropdownMenu`. It is the M3 Expressive vertical menu: 16dp container, 48dp rows with an inset
12dp selection pill, tonal `surfaceContainer` container, a shadow, and it sizes itself to its
widest row up to the screen width. Mark the current choice with `selected = ...`.

## Copy and layout

Say the thing once. No captions under cards explaining what the card just showed, no sentences
under a button repeating its label, no empty-state prose that restates the buttons beneath it.
Error messages and genuine how-to instructions (a search field's prompt, "pick your name on the
balances tab") are not that — keep them.

## Colour and shapes

Fixed palette in `ui/theme/Palette.kt` (no dynamic colour). Accent emerald, money positive/negative
green/red, iOS-style grouped neutrals, monogram palette. Shapes are Material's rounded up:
`SpliitShapes.Material`. Text styles are `SpliitType`; do not introduce a new type scale.

## Tests

Robolectric renders the Compose screens on the JVM: see `app/src/test/java/app/spliit/ui/...` and
`FakeSpliitServer`. A screen change that alters what is on screen usually needs its test updated
in the same commit. `ExpenseListTabTest` has a known flake in the full suite (a
`ComposeTimeoutException`) and passes on its own.
