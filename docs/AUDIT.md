# Phase 0 — `spliit-ios` codebase audit

Read against:
`ref-spliit-ios` (SwiftUI reference, commit at clone time) and `ref-spliit-web`
(Next.js backend: `prisma/schema.prisma`, `src/lib/{shares,balances,schemas}.ts`).

Code volumes: 25,748 lines of Swift. Two SwiftPM targets under
`Packages/SpliitKit/`:

| Target | Role |
|---|---|
| `SpliitAPI` | tRPC transport, superjson envelope, DTOs, endpoint definitions |
| `SpliitCore` | Pure, unit-tested logic: splits, money, dates, persistence models |
| `Spliit` (app) | Views, view models, design system |

The split between `SpliitCore` and views is the port's best asset: everything that
decides a number is already view-free and covered by 16 test files, so Kotlin can be
tested against the same expectations.

---

## 1. Design tokens

### 1.1 Colour (from `Assets.xcassets`, light / dark)

| Token | Light | Dark | Use |
|---|---|---|---|
| `AccentColor` | `#059669` | `#10B981` | Global tint, links, buttons, "Select all" |
| `MoneyPositive` | `#047857` | `#34D399` | Owed to you |
| `MoneyNegative` | `#C2334A` | `#FF8A9B` | You owe |
| `BrandAccentSoft` | `#ECFDF5` | `#10B981` @ 16% | Tile behind empty-state art |
| `BrandSecondary` | `#BE185D` | `#EC4899` | Defined, currently unused |

Monogram palette (identical in both themes, no dark variant):

| | Hex | Name |
|---|---|---|
| `Monogram1` | `#059669` | emerald |
| `Monogram2` | `#0891B2` | cyan |
| `Monogram3` | `#6366F1` | indigo |
| `Monogram4` | `#BE185D` | pink |
| `Monogram5` | `#EA580C` | orange |
| `Monogram6` | `#CA8A04` | amber |
| `Monogram7` | `#4D7C0F` | olive |
| `Monogram8` | `#7C3AED` | violet |

These values were re-derived directly from each `*.colorset/Contents.json`
(`color-space: srgb`, components as `0xRRGGBB`, second entry = dark appearance)
and match exactly.

Everything else is a **system** colour on iOS: `secondarySystemGroupedBackground`
for cards, `tertiarySystemFill` for the category icon slot, `.primary` /
`.secondary` for text, `.separator`. The Android equivalent is a Material 3
`ColorScheme` whose neutrals are overridden to iOS grouped-list values
(`#F2F2F7` light / `#000000` dark backgrounds, `#FFFFFF` / `#1C1C1E` cards), with
`primary = #059669`. No dynamic colour / Material You: the iOS palette is fixed on
every Android version.

### 1.2 Typography

| Role | iOS | Compose |
|---|---|---|
| Money `.hero` | `.largeTitle` rounded, tabular, tracking −0.2 | 34sp Rounded, `FontFeature("tnum")` |
| Money `.lead` | `.title2` rounded tabular | 22sp Rounded |
| Money `.row` | `.body` rounded tabular | 17sp Rounded |
| Money `.support` | `.footnote` rounded tabular | 13sp Rounded |
| Screen/empty-state title | `.title2`/`.title3` rounded semibold | 22sp Rounded SemiBold |
| Date bucket header | `.caption` bold, **small caps, wide tracking**, secondary | 12sp, uppercase, letterSpacing 0.8sp |
| Body / row title | `.body` | 17sp default |
| Secondary line | `.footnote`/`.subheadline` secondary | 13sp secondary |

No custom font file ships with the app: SF Rounded is a system face. On Android the
closest faithful substitute is the system sans with `FontFamily.SansSerif` and
`FontFeature("tnum")`, or a bundled rounded face — decided in Phase 2 when the first
money screen is built, recorded here as the one unavoidable typography substitution.

### 1.3 Spacing, radii, structure

- 4pt spacing scale; **no token file** — `List`/`Form` supply all insets.
  Android equivalent: `LazyColumn` + grouped cards with 16dp horizontal padding,
  10–12dp vertical gaps, 20dp card corner radius, 12dp inner padding.
- Radii: 8 / 12 / 16 in the design system, used as 10 (tile), 12 (card), 20
  (capsule for undo bar/search field).
- Monogram sizes: 40 group row, 26 balance row / information, 24 reimbursement
  arrow sides, 7–9 dot.
- Empty-state art: 64pt tile, `BrandAccentSoft`, icon 28pt.
- Motion: single `easeInOut 0.22s`; no springs, no zoom transitions.

### 1.4 SF Symbol → Material icon map

Every name below was checked against the real artifacts (`material-icons-core`
1.7.8 and `material-icons-extended` 1.7.8, the versions Compose BOM 2025.05.01
resolves), not from memory. The **Src** column says which artifact provides it —
`core` ships transitively with Material 3, `ext` needs the explicit
`androidx.compose.material:material-icons-extended` dependency, `auto` is
`Icons.AutoMirrored.Filled` (directional, RTL-aware).

| SF Symbol | Material icon | Src |
|---|---|---|
| `list.bullet` | `AutoMirrored.Filled.List` | auto |
| `arrow.left.arrow.right` | `Filled.SwapHoriz` | ext |
| `chart.pie` | `Filled.PieChart` | ext |
| `info.circle` | `Filled.Info` | core |
| `plus` | `Filled.Add` | core |
| `pencil` | `Filled.Edit` | core |
| `magnifyingglass` | `Filled.Search` | core |
| `ellipsis` / `ellipsis.circle` | `Filled.MoreVert` | core |
| `star` / `star.fill` | `Filled.Star` (tinted when set) | core |
| `archivebox` | `Filled.Archive` | ext |
| `trash` | `Filled.Delete` | core |
| `checkmark` | `Filled.Check` | core |
| `xmark` | `Filled.Close` | core |
| `chevron.right` | `Filled.ChevronRight` | ext |
| `chevron.down` | `Filled.ExpandMore` | ext |
| `square.and.arrow.up` | `Filled.Share` | core |
| `doc.on.doc` | `Filled.ContentCopy` | ext |
| `qrcode.viewfinder` | `Filled.QrCodeScanner` | ext |
| `camera` / `photo` | `Filled.PhotoCamera` / `Filled.Image` | ext |
| `exclamationmark.triangle` | `Filled.Warning` | core |
| `arrow.clockwise` | `Filled.Refresh` | core |
| `banknote` (fallback) | `Filled.Payments` | ext |
| `ticket` | `Filled.ConfirmationNumber` | ext |
| `dice` | `Filled.Casino` | ext |
| `movieclapper` | `Filled.Movie` | ext |
| `music.note` | `Filled.MusicNote` | ext |
| `dumbbell` | `Filled.FitnessCenter` | ext |
| `fork.knife` | `Filled.Restaurant` | ext |
| `menucard` | `Filled.MenuBook` | ext |
| `cart` | `Filled.ShoppingCart` | core |
| `wineglass` | `Filled.WineBar` | ext |
| `house` | `Filled.Home` | core |
| `powerplug` | `Filled.Power` | ext |
| `chair.lounge` | `Filled.Chair` | ext |
| `lamp.table` | `Filled.Light` | ext |
| `wrench.adjustable` | `Filled.Build` | core |
| `building.columns` | `Filled.AccountBalance` | ext |
| `cat` | `Filled.Pets` | ext |
| `key` | `Filled.Key` | ext |
| `wrench.and.screwdriver` | `Filled.Handyman` | ext |
| `stroller` | `Filled.ChildFriendly` | ext |
| `tshirt` | `Filled.Checkroom` | ext |
| `hand.raised` | `Filled.PanTool` | ext |
| `books.vertical` | `Filled.LibraryBooks` | ext |
| `gift` | `Filled.CardGiftcard` | ext |
| `stethoscope` | `Filled.MedicalServices` | ext |
| `bus` | `Filled.DirectionsBus` | ext |
| `bicycle` | `Filled.DirectionsBike` | ext |
| `tram` | `Filled.Tram` | ext |
| `car` | `Filled.DirectionsCar` | ext |
| `fuelpump` | `Filled.LocalGasStation` | ext |
| `bed.double` | `Filled.Hotel` | ext |
| `parkingsign` | `Filled.LocalParking` | ext |
| `airplane` | `Filled.Flight` | ext |
| `car.side` | `Filled.LocalTaxi` | ext |
| `sparkles` | `Filled.AutoAwesome` | ext |
| `bolt` | `Filled.Bolt` | ext |
| `thermometer.sun` | `Filled.Thermostat` | ext |
| `phone` | `Filled.Phone` | core |
| `drop` | `Filled.WaterDrop` | ext |

Category key → icon is `"<grouping>/<name>"`, 47 entries, fallback `banknote`
(`Shared/ExpenseCategoryIcon.swift`).

Every one of the 62 names above resolves in 1.7.8 — no substitution was needed.
Two notes for Phase 1: `material-icons-extended` is frozen at 1.7.8 and will not
move with newer BOMs, so it is pinned through BOM 2025.05.01 and has to be
re-pinned if the BOM is upgraded; and only seven of the core glyphs are
automirrored, so `List` must be referenced as `Icons.AutoMirrored.Filled.List`.

## 2. Navigation map

```
GroupsListView  (NavigationStack, wordmark toolbar leading, + trailing, settings bottom bar)
├── push → GroupDetailView(groupID)   inline title = group name
│     TabView: Expenses · Balances · Totals · Information · Search(role: .search)
│     ├── Expenses  (list.bullet)      grouped list, + → New expense sheet
│     ├── Balances  (swap)             You section, balance rows, suggested reimbursements
│     ├── Totals    (pie)              group total, your spending/share, by-category bars
│     ├── Information (info)           note, participants, currency/created, who are you
│     │     └── push → ActivityLogView (bucketed by today/yesterday/…)
│     └── Search                       docked field, server-side filter
│     Sheets: new expense · edit expense · settle reimbursement · group settings · who are you
├── sheet → GroupFormView(create)      "Create group"
├── sheet → AddGroupByURLView          "Add group" (paste ID or spliit.app URL)
├── sheet → SettingsView               default server URL, about
└── fullScreenCover → ScanGroupQRCodeView
```

Onboarding: first launch with no groups → welcome screen drawing the wordmark at
60pt with "Create a group" / "Join a group" and an empty state; `navigationTitle`
is empty there and "Groups" on the list (the title names back buttons).

Behaviour to mirror in Compose:

- Sheets → `ModalBottomSheet` (iOS sheet) for group form / expense form / settings /
  identity; full-screen for QR scan.
- Segmented split picker → `SingleChoiceSegmentedButtonRow`.
- Swipe-to-delete with 5s undo bar → `SwipeToDismissBox` + snackbar-like undo bar
  bound to the tab content, 5s window.
- Pull-to-refresh → `PullToRefreshBox`.
- Tab bar minimises on scroll → Material 3 `BottomAppBar` hide-on-scroll.
- No dynamic colour, no blur/RenderEffect (API 31+).

---

## 3. Business logic (the exact rules)

### 3.1 Money

- Every amount is an **integer count of minor units** on the wire: `1234` = 12.34.
- Minor-unit digits come from the ISO currency code (`MoneyFormatter`):
  0 for JPY/KRW/etc., 2 default, 3 for BHD/KWD/OMR/TND/JOD/LYD/IQD.
- Groups with only a free-text symbol assume 2.
- Parsing accepts a decimal comma, a dot, and arithmetic expressions
  (`12+3.50`, `10*0.5`, `÷`, `×`, `−`, parentheses, max 512 chars, full-input
  required). Rounds half-up to an integer.

### 3.2 Split apportionment — `ExpenseShares` (ported from `src/lib/shares.ts`)

```
shares(expenseId, amount, splitMode, paidFor):
  rows   = paidFor sorted by participantId (UTF-16 code-unit order, JS `<`)
  weights = EVENLY ? [1,1,…] : rows.map(shares)
  total   = Σ weights (Int128)
  offset  = expenseId non-empty && rows non-empty
              ? fnv1a32(expenseId) % rows.size : 0
  for each row:
      exact      = amount * weight
      base       = floor(exact / total)          # toward −∞, not toward 0
      remainder  = exact − base*total
  remaining = amount − Σ base                     # always in [0, rows.size)
  sort by remainder desc, ties by ((index − offset + n) % n) asc
  give +1 minor unit to the first `remaining` rows
```

- `fnv1a32`: offset basis `0x811c9dc5`, prime `0x01000193`, over UTF-16 units,
  result unsigned 32-bit.
- Shares always sum to **exactly** the amount, including negative (income) amounts.
- `BY_PERCENTAGE` / `BY_AMOUNT` are treated as a **ratio** at read time, so rows
  that do not sum to 10000 / total still divide the whole amount.
- `BY_SHARES` and `BY_PERCENTAGE` share values are ×100 (`2` shares = `200`,
  `33.5%` = `3350`). `BY_AMOUNT` shares are raw minor units.
- Empty `total` (all zero weights) → every share 0.

### 3.3 Per-expense balance

```
if payer != me and I have no share → null (not involved)
else → (I paid ? amount : 0) − (my share ?? 0)
```

Reading of a non-zero balance: reimbursement → `paidBack`/`wasPaidBack`;
negative amount → `isOwed`/`owes`; else `lent`/`borrowed`.

### 3.4 Group balances & reimbursement (server + client agree)

```
paid[p]    += amount              (payer)
paidFor[p] += share(p)
total[p]    = paid − paidFor
```
`groups.balances.list` returns **public** balances derived from suggested
payments (one of paid/paidFor always 0). The app's "You" section uses
`groups.stats.get/overview` for real spending, not those.

Debt simplification (`getSuggestedReimbursements`):

```
list = balances with total != 0, sorted: positives first, then by participantId
while list.size > 1:
   first = list[0], last = list[-1]
   if first.total > -last.total:
        emit  from=last → to=first, amount=-last.total
        first.total += last.total; pop last
   else:
        emit  from=last → to=first, amount=first.total
        last.total  += first.total; shift first
drop zero-amount results
```

### 3.5 Form validation (`ExpenseFormDraft.problems`)

| Rule | Detail |
|---|---|
| Title | trimmed length ≥ 2 |
| Amount | present, parses, ≠ 0, ≤ 1,000,000,000 minor units |
| Payer | required |
| Paid-for | ≥ 1 included participant |
| Shares (non-even) | each included share parses and > 0 |
| `BY_AMOUNT` | Σ shares == amount (else `amountsDoNotSumToTotal`) |
| `BY_PERCENTAGE` | Σ shares == 10000 (else `percentagesDoNotSumTo100`) |
| Conversion | original amount present ≠ 0, rate present > 0 |

Derived amounts: `amount = round(originalAmount/10^dOrig × rate × 10^dGroup)`;
the amount field is read-only under conversion.

### 3.6 Date grouping

Expense buckets (newest first): Upcoming (future), This week, Earlier this month,
Last month, Earlier this year, Last year, Older.
Activity buckets, finer: Today, Yesterday, Earlier this week, Last week,
Earlier this month, Last month, Earlier this year, Last year, Older.
`expenseDate` is a bare date → midnight **local**; `createdAt` is a timestamp.

### 3.7 Monograms

- Participant colour = **position in the group** mod 8.
- Group colour = FNV-1a 64-bit of the **group ID** mod 8
  (offset `0xcbf29ce484222325`, prime `0x100000001b3`).
- Initials = first letter of up to two whitespace-separated words, uppercased.

---

## 4. Local persistence

`Application Support/Spliit/recent-groups.json` — a plain JSON **array** of
`RecentGroup` (property names match the old React Native app):

```json
{
  "groupId": "abc123",
  "groupName": "Flat",
  "isStarred": false,
  "isArchived": false,
  "activeParticipant": "<participantId>" | "",   // "" = explicitly nobody
  "defaultSplit": { "splitMode": "EVENLY", "shares": {"id": 100} },
  "updatedAt": "ISO-8601",
  "lastOpenedAt": "ISO-8601",
  "instanceURL": "https://spliit.app/"
}
```

- iCloud mirror is a `RecentGroupsSnapshot {groups, deleted}` with 90-day
  tombstones and last-writer-wins per row. **Android has no iCloud**: the phone
  file is the only copy; tombstones are unnecessary and will not be ported.
- Three sections: starred (not archived), recent (neither), archived.
- `activeParticipant` has 3 states: unanswered (`null`), a participant, or
  explicit "nobody" (`""`). A participant removed from the group reads as
  unanswered.
- `defaultSplit` applies only if every remembered participant still exists;
  otherwise it is ignored whole. `BY_AMOUNT` remembers only its mode.
- Android: DataStore (Preferences or Proto) holding the same JSON array, file
  semantics kept 1:1 so the same rules apply.

---

## 5. Network contract

Transport (`TRPCClient`):

- Base URL = instance root, with or without trailing slash; per-group.
- Query → `GET {base}/api/trpc/{path}?input={superjson}` (percent-encoded).
- Mutation → `POST {base}/api/trpc/{path}`, body = superjson envelope.
- Timeouts 20s request / 30s resource; no caching; no cookies; no batching.
- Success: `{"result":{"data":{"json": …}}}`.
- Error: `{"error":{"json":{"message","data":{"code","httpStatus","path"}}}}`;
  `NOT_FOUND` on unknown procedure = feature unavailable, not a crash.
- superjson `meta.values` maps dotted paths → `["Date"]`. Encoding must annotate
  `expenseDate` as a Date (sent as midnight UTC of the local calendar day).

Procedures used:

| Procedure | Kind | Input |
|---|---|---|
| `groups.list` | query | `{groupIds: [String]}` |
| `groups.get` | query | `{groupId}` |
| `groups.getDetails` | query | `{groupId}` |
| `groups.create` | mutation | `{groupFormValues}` |
| `groups.update` | mutation | `{groupId, groupFormValues, participantId?}` |
| `groups.expenses.list` | query | `{groupId, cursor, limit, filter?}` |
| `groups.expenses.get` | query | `{groupId, expenseId}` |
| `groups.expenses.create` | mutation | `{groupId, expenseFormValues, participantId?, expenseId?}` |
| `groups.expenses.update` | mutation | `{groupId, expenseId, expenseFormValues, participantId?}` |
| `groups.expenses.delete` | mutation | `{groupId, expenseId, participantId?}` |
| `groups.balances.list` | query | `{groupId}` |
| `groups.stats.overview` → fallback `groups.stats.get` | query | `{groupId, participantId?}` |
| `groups.activities.list` | query | `{groupId, cursor, limit}` |
| `categories.list` | query | none |

`ExpenseFormValues` encoding detail: `originalCurrency` is **always emitted**,
as `null` when absent (otherwise Prisma leaves the stale column); `originalAmount`
and `conversionRate` are omitted, because the server rejects `null` for them.

Server schema (`prisma/schema.prisma`) agrees: `Group.currency String @default("$")`,
`currencyCode String?`, `Expense.amount Int`, `ExpensePaidFor.shares Int`,
`SplitMode {EVENLY, BY_SHARES, BY_PERCENTAGE, BY_AMOUNT}`.

---

## 6. iOS → Kotlin file mapping (Phase 1–4)

| iOS | Kotlin (package `app.spliit`) |
|---|---|
| `SpliitAPI/Models.swift` | `data/model/*.kt` (`Group`, `Participant`, `ExpenseListItem`, `ExpenseDetails`, `Balance`, `Reimbursement`, `Activity`, form values) |
| `SpliitAPI/TRPCClient.swift` | `data/remote/TrpcClient.kt` (OkHttp) |
| `SpliitAPI/TRPCProcedure.swift` | `data/remote/TrpcProcedure.kt` |
| `SpliitAPI/SuperJSON.swift` | `data/remote/SuperJson.kt` |
| `SpliitAPI/SpliitEndpoints.swift` | `data/remote/SpliitApi.kt` |
| `SpliitAPI/NanoID.swift`, `CalendarDay.swift` | `util/NanoId.kt`, `util/CalendarDay.kt` |
| `SpliitCore/ExpenseShares.swift` | `domain/ExpenseShares.kt` |
| `SpliitCore/ExpenseBalance.swift` | `domain/ExpenseBalance.kt` |
| `SpliitCore/MoneyFormatter.swift` | `domain/MoneyFormatter.kt` |
| `SpliitCore/Currency.swift` | `domain/CurrencyCatalog.kt` |
| `SpliitCore/DefaultSplit.swift` | `domain/DefaultSplit.kt` |
| `SpliitCore/ExpenseFormDraft.swift` | `domain/ExpenseFormDraft.kt` (pure validation) |
| `SpliitCore/ExpenseDateGroup.swift`, `ActivityDateGroup.swift` | `domain/DateGroups.kt` |
| `SpliitCore/MonogramPalette.swift` | `ui/theme/MonogramPalette.kt` |
| `SpliitCore/RecentGroupsStore.swift` | `data/local/RecentGroupsRepository.kt` + `data/local/RecentGroupEntity.kt` (DataStore) |
| `GroupsListView.swift` | `ui/groups/GroupsScreen.kt` |
| `GroupDetailView.swift` | `ui/group/GroupScreen.kt` (5 tabs) |
| `ExpenseListView.swift` + `ExpenseRow.swift` | `ui/expenses/ExpenseListScreen.kt`, `ExpenseRow.kt` |
| `ExpenseFormView.swift` | `ui/expenses/ExpenseFormScreen.kt` |
| `BalancesView.swift` | `ui/balances/BalancesScreen.kt` |
| `StatsView.swift` | `ui/stats/TotalsScreen.kt` |
| `GroupInformationView.swift` | `ui/group/InformationScreen.kt` |
| `ActivityLogView.swift` | `ui/activity/ActivityScreen.kt` |
| `GroupFormView.swift` | `ui/group/GroupFormScreen.kt` |
| `ActiveUserPickerView.swift` | `ui/group/ActiveUserPicker.kt` |
| `AddGroupByURLView.swift` | `ui/groups/AddGroupByUrlScreen.kt` |
| `SettingsView.swift`, `InstanceField.swift` | `ui/settings/SettingsScreen.kt` |
| `Views/DesignSystem/*` | `ui/theme/*` (`Palette`, `Money`, `Monogram`, `EmptyState`, `CategoryIcon`, `DateBucketHeader`, `UndoBar`, `Motion`) |
| `Shared/ExpenseCategoryIcon.swift` | `ui/theme/CategoryIcons.kt` |
| `GroupDetailModel.swift` | `ui/group/GroupViewModel.kt` (`StateFlow`) |
| `AppModel.swift` | `SpliitApplication` + `AppContainer` (Hilt) |

---

## 7. Build environment (settled)

The host had no Android SDK, no Android Studio and only JDK 25 (AGP 8.x does not
support 25). Everything is provisioned **inside the workspace** rather than
system-wide, under `spliit-workspace/.tooling/`:

| Component | Version | Location |
|---|---|---|
| JDK | Temurin 21.0.13.1+2 | `.tooling/jdk21` |
| Android cmdline-tools | 11076708 | `.tooling/sdk/cmdline-tools/latest` |
| Android SDK platform | `android-35` | `.tooling/sdk/platforms` |
| Build tools | `35.0.0` | `.tooling/sdk/build-tools` |
| Platform tools | latest | `.tooling/sdk/platform-tools` |
| Gradle | 8.13 | wrapper, distribution from `.tooling/gradle` |

Wiring:

- `gradle.properties` → `org.gradle.java.home=<workspace>/.tooling/jdk21`
- `local.properties` (git-ignored) → `sdk.dir=<workspace>/.tooling/sdk`
- `build.gradle.kts` → AGP 8.9.2, Kotlin 2.1.21, compose compiler plugin
- `app/build.gradle.kts` → `compileSdk 35`, `minSdk 26`, `targetSdk 35`,
  `isCoreLibraryDesugaringEnabled = true`, `desugar_jdk_libs 2.1.5`,
  JVM target 17, R8 + resource shrinking on release

`assembleDebug` of a hello-world Compose activity (which also references
`java.time.LocalDate`, exercising desugaring) is the acceptance check for the
toolchain.

## 7.1 Verification method

- Colours: parsed every `Assets.xcassets/*.colorset/Contents.json` and rendered
  the light and dark entries to hex.
- Icons: downloaded `material-icons-core-android-1.7.8.aar` and
  `material-icons-extended-android-1.7.8.aar`, unzipped `classes.jar`, and
  enumerated `androidx/compose/material/icons/{filled,automirrored/filled}/*Kt`
  — 2083 extended glyphs, 49 core glyphs, 138 + 7 automirrored.
- Toolchain: JDK 21 and the SDK packages are asserted by an actual
  `:app:assembleDebug`, not by the presence of files.

## 8. Deliberate scope boundaries

Out of MVP scope (present in the iOS app, not requested): receipt scanning,
document uploads, QR scanning, App Intents/Spotlight, widgets, iCloud sync,
CSV/JSON export, analytics, French localisation, recurring-expense UI (the model
field is preserved on save so edits do not drop it).
