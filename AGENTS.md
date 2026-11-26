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

When a spinner really is right, it is `CircularProgressIndicator` sized to what it sits in — see the
loading note under "Material 3 Expressive".

## Material 3 Expressive

The app is built on Material 3 Expressive. `app/build.gradle.kts` pins
`androidx.compose.material3:material3:1.4.0` (it includes `MaterialShapes`, `LoadingIndicator` and
the expressive component set) and `androidx.graphics:graphics-shapes:1.0.1` (the `RoundedPolygon`
and morph engine underneath). The rest of Compose comes from BOM 2026.06.01; the toolchain is
AGP 8.13.2, Kotlin 2.3.21, compileSdk and targetSdk 36. Material3 1.5.0 resolves to Compose UI
1.12, which requires compileSdk 37 and AGP 9.1, so it cannot be bumped on its own.

Rules for any new screen or component:

- **Opt in, don't fall back.** Use `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` for the
  expressive APIs instead of reimplementing the control.
- **Loading and progress are the framework's.** `LoadingIndicator`,
  `ContainedLoadingIndicator`, `PullToRefreshDefaults.LoadingIndicator`,
  `LinearWavyProgressIndicator` and `CircularWavyProgressIndicator` are the loading and progress
  controls; shape sequences come from `MaterialShapes` and `RoundedPolygon`. Never add a
  third-party loader, and never hand-draw a morphing polygon. The one exception is the app's own
  choice of *prominence*: the small in-page busy states (a list footer paging, a row that is
  saving) keep `CircularProgressIndicator`. The polygon indicator was tried there and rejected as
  too loud at 16–20dp; page loads still use skeletons.
- **Prefer the expressive component over a bespoke one.** `SplitButtonLayout`, `ButtonGroup`,
  `FloatingActionButtonMenu`, `HorizontalFloatingToolbar`, `VerticalFloatingToolbar` and
  `FlexibleBottomAppBar` exist; use them when a screen needs that thing rather than assembling it
  from `Surface`s. The app's own exceptions are `ExpressiveMenu` and the SwiftUI-style pill tab
  bars, described above.
- **Motion comes from the theme.** Springs from `SpliitMotion` (below), or `MotionScheme.expressive()`
  where a Material component takes a motion scheme. A screen must not carry its own timings.
- **Write it like the rest of the app.** State hoisted out of the composable, colour roles only
  (never literals), dynamic colour honoured, and a `@Preview` for a new screen.

## Motion

`ui/theme/Motion.kt` holds the springs. Motion is SwiftUI's, not Material's timed curves:
`SpliitMotion.snappy()`, `bouncy()`, `smooth()` and `swiftSpring(response, dampingRatio)` are the
same presets SwiftUI names, expressed as stiffness = (2π / response)² with a damping ratio.
Colour and opacity use the critically damped ones; anything that moves, sizes or reshapes uses a
spatial spring. `Modifier.pressScale(...)` is the touch response.

Two Compose animation libraries are the reference for how a motion is written, not extra
dependencies. Check a new animation against them before inventing one:

| Reference | What it is for |
| --- | --- |
| [skydoves/compose-animations](https://github.com/skydoves/compose-animations) | A catalogue of `androidx.compose.animation` patterns — `AnimatedVisibility` enter/exit, `AnimatedContent` transitions, `animate*AsState`, shared elements. Copy its pattern for a given animation rather than a hand-rolled keyframe loop. |
| [skydoves/Orbital](https://github.com/skydoves/Orbital) (`com.github.skydoves:orbital:0.4.0`) | Container transform and shared-element motion (`Orbital`, `OrbitalScope.animateTransformation`). It is the reference for a card growing into a page or a row growing into its editor. |

One gesture, one animator: a drag writes its offset directly and the settle is the only animation
running, with `draggable(enabled = …)` off while it runs (`ui/theme/Swipe.kt`). Snapping from a
coroutine per drag delta is what used to leave a row parked mid-swipe.

One layer for a moving shape: when something scales while it casts a shadow, the scale and the
elevation belong on the *same* `graphicsLayer` (`shadowElevation`, `shape`, `clip = false`).
`scaleIn` inside `AnimatedVisibility` plus `Modifier.shadow` on the child puts them on two render
nodes, and the render thread then recomputes the shadow a frame behind the shape — the menu and
the FAB action pills both used to trail their own shadow this way. The fixed forms are
`ui/theme/Menu.kt` and `FabActionPill` in `ui/groups/GroupsListScreen.kt`.

## Offline and CSV

A group's page keeps working without a network. Every successful load writes a snapshot to
`data/local/GroupCache.kt` (one JSON file per group in `cacheDir`), and a load that cannot reach
the server restores it, sets `GroupScreenModel.isOffline`, and the header says
"Offline · showing a saved copy". The server stays the truth: the snapshot is overwritten on every
successful read and after every write, and it is a cache, so losing it only costs a reload.

A change that could not be sent is kept, not lost. `data/local/ExpenseOutbox.kt` writes every
failed expense create, edit and delete to one JSON file in `filesDir` (never `cacheDir` — this is
data the person typed), and `GroupScreenModel` draws a queued create as a real row marked
"Waiting to send", folds it into search and totals, and sends the backlog the next time an
instance answers. A queued delete keeps its row hidden; a queued edit keeps the server's row,
because the newest version is what will be sent. The tests for a new mutation must say what
happens when it cannot reach the server.

A group's dates and participant counts are cached with the snapshots (`GroupCache.loadSummaries`
/ `saveSummaries`), so the home list keeps saying "Nov 6 · 3" with no signal. Offline, the totals
and the category breakdown are folded from the cached expenses, search matches titles locally,
and the activity log comes out of the snapshot.

A refresh nobody asked for is silent. The home list fetches its details again every time it is
opened, and a group page re-reads on entry — both keep what is already on screen (the local list,
the `GroupCache` snapshot) and swap in the server's answer when it arrives. `PullToRefreshBox`'s
indicator belongs to the pull gesture alone (`GroupsListModel.refresh(showIndicator = …)`), and a
page must never trade its content for a spinner or a skeleton to fetch fresh data.

CSV is **Splitwise's format**, both ways, in `domain/SplitwiseCsv.kt`:
`Date,Description,Category,Cost,Currency,<name>,<name>…`. Import and export are items in the
group header's overflow menu, beside Share, Edit group and Delete group — they are things done to
the group, not settings of it. The page behind "Edit group" holds only the group's own facts, with
Created and Server at the very bottom. Import reads a file, confirms the row count, then creates one expense per
row through the normal `groups.expenses.create` procedure — the file's columns name people, so a
name is resolved against the group's participants; when the person columns add up to the cost they
are shares (an `BY_AMOUNT` split), otherwise they are payments (an `EVENLY` split, the biggest
payer paying). Export sweeps every page and writes what each participant owes from
`ExpenseShares`, the same apportionment the balances tab uses, so its columns add up to the cost.

## Receipts: documents yes, scanning no

Spliit has **two** receipt features and they are not the same thing. Read this before touching
either, and read the "Design references" table below before drawing anything for them.

| Feature | Upstream switch | What this app does |
| --- | --- | --- |
| Expense documents (photos of the receipt attached to an expense) | `ENABLE_EXPENSE_DOCUMENTS` + `S3_UPLOAD_*` on the instance | **Implemented.** Pick a photo in the expense form, it uploads to the instance, the URL travels with the expense, and the detail screen shows the receipts. |
| Receipt extraction ("AI Scan": title, amount, date, category read off the photo) | `ENABLE_RECEIPT_EXTRACT` + `OPENAI_API_KEY` on the instance | **Deliberately not implemented.** It needs an OpenAI key from whoever runs the server, and this app must never ask for or ship one. Do not add it, and do not substitute a third-party model call. |

How documents work, since the difference from "the app's database" matters:

- The bytes live in the **instance's own object storage** — S3, or anything S3-compatible behind
  `S3_UPLOAD_ENDPOINT`. Postgres only ever holds `{ id, url, width, height }`. If an instance has
  the feature off, it has no bucket to sign for; the app shows "This Spliit server doesn't accept
  receipt photos" rather than pretending the network failed.
- The browser reaches the bucket through `next-s3-upload`: a presigned POST policy from
  `POST /api/s3-upload`, then the file straight to S3. `TrpcClient.uploadDocument` is that same
  exchange, so a photo taken here lands where a photo taken on the web lands and every member
  sees it. Keep the policy fields in the order the server sent them and put `file` last.
- `domain/ReceiptPhoto.kt` makes a picked photo uploadable: upright from EXIF, long edge sampled
  near 1800px, JPEG under Spliit's 5 MB ceiling, and it reports the width and height the server
  stores. Run it off the main thread.
- Picking a photo is the system's own chooser: one `ACTION_CHOOSER` offering the camera (writing
  into a `FileProvider` file under `cacheDir/receipts/`) and `ACTION_OPEN_DOCUMENT` for a file
  already on the phone. Do not put a bespoke in-app picker in front of it, and do not call
  `PickVisualMedia` directly — a picture taken here must come back as reliably as one chosen from
  storage.
- The UI is `ui/expenses/ReceiptAttachments.kt` (the form's strip: add, upload progress, remove)
  and `ui/expenses/ReceiptStrip.kt` (the detail screen's thumbnails and the full-size preview).
  Coil loads the images; there is no image cache of our own.

Both strips animate with `SpliitMotion` and use colour roles only, like every other screen.

## Starting up

The remembered group list is read from disk before anything is decided about it: until the
first emission, `GroupsListModel.hasLoadedGroups` is false and the screen draws the background
rather than the welcome empty state, so an existing install never sees "Welcome to Spliit" for a
frame. A one-shot 240ms timer shows the brand mark (`drawable/ic_mark.xml`, generated from
`spliit.svg`) only when the read is genuinely slow. The launcher icon is the artwork **with its
plate** (`spliit-bg.svg`): the foreground carries the full 108dp square, the adaptive background is
transparent, and the launcher's mask crops that square. The in-app mark is the transparent one,
tinted with a colour role so it follows light and dark. Do not add a splash screen in front of a
local read that takes milliseconds.

## Touch feedback

Every clickable row, card, pill and button shows a ripple, and the cards additionally dip with
`Modifier.pressScale`. Do not write `indication = null` on a `clickable`/`combinedClickable`;
pass `LocalIndication.current` when a custom `interactionSource` is needed for the scale, or let
`clickable` use the default. Material components (`Surface(onClick = …)`, `Button`, `IconButton`)
already ripple — do not re-implement them.

## Elevation

Tone first, shadow second. Static containers separate by `surfaceContainer*` colour, never by a
drop shadow. Only things that genuinely float above the page take a shadow — the menu, the action
pills the add button unfolds, and the undo bar — using the numbers in `SpliitElevation`. The FAB
keeps Material's own shadow. Do not add `Modifier.shadow` to a card, row, bar or tab bar.

## Menus

Use `ExpressiveMenu` / `ExpressiveMenuItem` from `ui/theme/Menu.kt`; never a bare
`DropdownMenu`. The menu sizes itself with `width(IntrinsicSize.Max)` so it is as wide as its
widest row: rows carry `fillMaxWidth()`, and without the intrinsic width the popup would hand
them the whole screen and every menu would span it. It is the M3 Expressive vertical menu: 16dp container, 48dp rows with an inset
12dp selection pill, tonal `surfaceContainer` container, a shadow, and it sizes itself to its
widest row up to the screen width. Mark the current choice with `selected = ...`.

## Copy and layout

Say the thing once. No captions under cards explaining what the card just showed, no sentences
under a button repeating its label, no empty-state prose that restates the buttons beneath it.
Error messages and genuine how-to instructions (a search field's prompt, "pick your name on the
balances tab") are not that — keep them.

## Colour and shapes

Palette in `ui/theme/Palette.kt`, templates in `ui/theme/Templates.kt`. On Android 12 and later
the app uses Material's adaptive colour (`dynamicLight/DarkColorScheme`, from the wallpaper) unless
the switch in the home screen's appearance sheet is turned off; older phones, and the off switch,
fall back to a template. Every template — Iris, Lagoon, Ember, Meadow — is generated by Google's
Material Color Utilities in `PaletteStyle.Expressive`, and every extra role a template shows is
read back out of that scheme: the monograms and the empty-state tile are containers with their own
`on-` colours, and the two money tints are built by `semanticTint` — emerald and coral red, leaned
towards the palette's accent and sharing its chroma, so they belong to the theme while still
reading as green and red. No literal colour belongs in a template path.

Draw from these roles, as the references in "Design references" define them:

| What is drawn | Role |
| --- | --- |
| Page background | `background` |
| Card, sheet, bar | `surfaceContainerLow` |
| Raised card, tab pill | `surfaceContainerHigh` |
| Filled tile, progress track, code block, chip | `surfaceContainerHighest` |
| Selected pill, selected menu row | `secondaryContainer` / `onSecondaryContainer` |
| Title text | `onSurface` |
| Supporting text and icons | `onSurfaceVariant` |
| Divider, hairline | `outlineVariant` (`outline` is for a real boundary, e.g. a field edge) |
| Accent fill and its content | `primary` / `onPrimary` |
| Undo bar | `inverseSurface` / `inverseOnSurface` |
| Destructive text and icon | `error` |

`surfaceVariant` is **not** a Material 3 role - use `surfaceContainerHighest`. Never mix a
foreground with a fill it was not measured against; `onSurfaceVariant` and `onSurface` are the
only two that belong on every surface container. `PaletteTemplatesTest` renders every palette,
Spliit's included, in both modes and checks every pairing a component draws clears AA 4.5:1.
Spliit's own palette (accent emerald, iOS neutrals, the eight monograms) is the single hand-authored
exception, kept from the iOS asset catalogue. Shapes are Material's rounded up:
`SpliitShapes.Material`. Text styles are `SpliitType`; do not introduce a new type scale.

## Design references

When a Material 3 question is not answered here, read one of these before inventing an answer.
They document the same system the app is built on, so guidance from them wins over habit:

| Reference | What it is for |
| --- | --- |
| [hamen/material-3-skill](https://github.com/hamen/material-3-skill) | `skills/material-3/SKILL.md` plus references for colour roles, tonal palettes, dynamic colour, components, type/shape/motion and navigation, with Compose-first mappings. Also has an MD3 compliance audit that scores an app across ten categories. |
| [meticha/material-3-expressive-catalog](https://github.com/meticha/material-3-expressive-catalog) | A running Jetpack Compose catalogue of M3 Expressive components — buttons and button groups, progress indicators, bottom app bar, FAB menu, floating toolbar. Read the component's sample before writing a new expressive control. |
| [skydashnet/material-design-3-ui-skill](https://github.com/skydashnet/material-design-3-ui-skill) | Design-side skill: `SKILL.md` routes to focused references for layout, navigation, forms, accessibility and expressive restraint. Use it for audits and for deciding whether expression belongs on a screen at all. |
| [android/compose-samples](https://github.com/android/compose-samples) | Official Compose samples. Jetchat and Reply show Material 3 theming with dynamic colour and light/dark; Jetcaster shows a custom theme with animated colours; Material Catalog shows every component's current behaviour. |

Before drawing or changing any screen or component, read at least the colour-roles reference in
[hamen/material-3-skill](https://github.com/hamen/material-3-skill) and check the component in
[meticha/material-3-expressive-catalog](https://github.com/meticha/material-3-expressive-catalog):
the role table under "Colour and shapes", the component set under "Material 3 Expressive" and the
motion rules above come from them. Receipts are the one feature with a half that is intentionally
missing — see "Receipts" above before touching it.

The rule they all point at: **Material 3 expressive is the default look of this app.** Colours come
from `ColorScheme` roles (never literals in a screen), surfaces separate by tone, shape and motion
come from the tokens here, and expression is spent on the few things that deserve it. The exception
is the tab bars: they stay SwiftUI-style pills, as described under "Colour and shapes". The pill is
a row of the header — under the title row, above the pages, on the page's own background — so no
content passes behind it and it needs no scrim, gradient or fade (the pages no longer carry a top
inset for it either).

## Tests

Robolectric renders the Compose screens on the JVM: see `app/src/test/java/app/spliit/ui/...` and
`FakeSpliitServer`. A screen change that alters what is on screen usually needs its test updated
in the same commit. `ExpenseListTabTest` has a known flake in the full suite (a
`ComposeTimeoutException`) and passes on its own.
