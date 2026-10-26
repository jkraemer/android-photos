# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

An iOS-style camera roll (`net.jkraemer.photos`): a vertical timeline grid of
`DCIM/Camera/` with four pinch zoom levels, and a detail view with film strip,
share and trash. Kotlin, Compose, minSdk 35.

## Commands

Build with JDK 21 (sources target JVM 17):

```sh
./gradlew test                        # all unit tests (core on the JVM, app with Robolectric)
./gradlew :app:testDebugUnitTest --tests '*GridScreenTest*'   # one test class
./gradlew assembleDebug
```

Unit tests run on the debug variant only: Compose UI tests need
`ui-test-manifest`'s activity, which only the debug variant merges.

Local release build (installs over a CI-signed release, which a debug APK cannot):

```sh
./gradlew assembleRelease -PversionCode=$(git rev-list --count HEAD)
```

Signing reads `RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD` from the environment; without them `assembleRelease`
produces an unsigned APK.

## Module seam

- **`core`** (plain Kotlin/JVM) decides: `TimelineGrid` (entries and headers
  per `ZoomLevel`), `DateLabels`, `newestFullyVisible`, `filmStripWindow`,
  `scrubTarget`, `neighbourAfterRemoval`, `MediaAccess`.
- **`app`** touches the system: `MediaStoreRepository` (query + observer
  flow), `MediaThumbnailFetcher` (Coil, via `ContentResolver.loadThumbnail`),
  `MediaActions` (share, trash request), `PermissionGate`, `GridScreen`,
  `DetailScreen`, `FilmStrip`, `VideoPage`. `MainActivity` is the composition
  root; the detail view overlays the grid so the grid keeps its scroll state.

## Constraints that shape the code

- **Library versions are capped by compileSdk 36 / AGP 8.13.2.** Compose BOM
  2026.08+, lifecycle 2.11+ and Coil 3.6+ demand compileSdk 37 (Compose 1.12
  also AGP 9.1). Check a library's `aar-metadata.properties` before bumping.
- **`GridScreen` lays out from the built model's level, not the requested
  one.** Switching columns before the new entries exist scrolls the old grid
  and loses the position the new model is about to restore.
- **`GridScreen` ignores an anchor equal to the one it last reported.** The
  caller echoes reports back; acting on the echo would yank the grid mid-fling.
- **`DetailScreen` reads items through `rememberUpdatedState` inside
  `BoxWithConstraints`.** The pager's page count updates before that
  subcomposition, so a captured list goes out of bounds when an item arrives.
- **The detail view follows its item by id.** Other apps (Immich freeing
  space) remove items while it is open; it then lands on the older neighbour.

## On the device (Pixel 10 Pro, GrapheneOS)

- Google Camera saves a RAW shot as `….RAW-01.COVER.jpg` plus
  `….RAW-02.ORIGINAL.dng`, both directly in `DCIM/Camera/`; only the MIME
  filter keeps the DNG out.
- telephoto 0.19.0 leaks one file descriptor per full-size photo it checks
  (`SubSamplingImageSource.exists()` never closes its `peek()` stream).
  The system logs "A resource failed to call close" when it is finalized.
  This comes from the library; the app's own code doesn't cause it.
- Robolectric attaches activities to the application's `ContextImpl`, so a
  leak of an activity through its `ContentResolver` cannot be reproduced
  there; check `adb shell dumpsys meminfo net.jkraemer.photos` (`Activities:`)
  after rotating instead.

## Testing notes

- Robolectric screens run at density 1: a 412dp screen is 412px wide, so
  gesture coordinates must stay inside that.
- The grid builds its entries on `Dispatchers.Default`, which the Compose test
  clock does not track; tests wait with `waitUntil`.
- ExoPlayer always looks up the `media_metrics` service; `VideoPageTest`
  registers a no-op one so Robolectric does not print a stack trace.
- Before adding a test, name the production change that would make it fail.

## Releasing

User-visible changes go under `## [Unreleased]` in `CHANGELOG.md`, in the same
branch as the change. To release: rename `[Unreleased]` to `[X.Y.Z] - <today>`
leaving a fresh empty `[Unreleased]` above it, commit, tag `vX.Y.Z` (lightweight),
and push the branch and tag to **both** remotes — `origin` is the primary forge,
`gh` is the GitHub mirror where the release workflow lives.

## Conventions

- Comments say WHAT and WHY, never what changed or when.
- `docs/superpowers/` holds design specs and plans and is deliberately untracked
  (excluded by a global gitignore) — leave it that way.
