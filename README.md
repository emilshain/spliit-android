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

## Status

Phase 0 — workspace setup and codebase audit.
