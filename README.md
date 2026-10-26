# Photos

An iOS-style camera roll for Android.

The stock gallery on GrapheneOS scrolls sideways, mixes screenshots,
downloads and messenger images in with the camera's photos, and has no way to
zoom the grid or see where in time you are. This app shows only what the
camera took, the way the iOS Photos "Library" view does: one vertical
timeline, newest at the bottom, opened right there.

Organizing, albums and search are left to [Immich](https://immich.app).

## What it does

**Library** — every photo and video in `DCIM/Camera/` (RAW files excluded;
their JPEG shows). Pinch steps between 3, 5, 15 and 30 columns and keeps the
photo under your fingers in place. At 3 and 5 columns a floating label names
the day or month of the newest photo fully on screen; at 15 and 30 columns
header rows mark each month or year.

**Detail** — swipe between photos (older to the left), pinch or double-tap to
zoom, tap to hide everything but the photo, swipe down to go back. The film
strip below runs through the whole timeline and moves along as you swipe;
fling it to scrub, tap a thumbnail to jump. Share opens the Android share sheet; delete moves the
item to the system trash, which empties itself after about 30 days. Videos
play inline with sound.

**Info** — swipe up on a photo or video to see when it was taken, its file
name, camera, lens, size and exposure, and where it was taken; swipe down or
press Back to put it away. The place name comes from the system's geocoder
(on GrapheneOS: Settings → Location → Location services → Geocoder) and
falls back to coordinates; tap it to open a maps app. The app itself has no
internet access.

## Requirements

- Android 15+ (minSdk 35)
- Full access to photos and videos ("Allow all"); with "Select photos" the
  app asks you to change that in its settings.
- Access to photo locations, asked for when you first open the info panel;
  without it the panel shows everything but the place.
- Developed against a Pixel on GrapheneOS with Google Camera.

## Building

JDK 21 and the Android SDK, then:

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Testing

```sh
./gradlew test    # unit tests: core logic on the JVM, screens with Robolectric
```

## Releasing

User-visible changes go in `CHANGELOG.md` under `[Unreleased]`, in the same
branch as the change. To release: rename `[Unreleased]` to the new version
with today's date (starting a fresh empty `[Unreleased]` above it), commit,
tag `vX.Y.Z`, and push the tag to GitHub — CI builds the signed APK and
publishes a release with that changelog section as its notes.

## Architecture

- **core** — plain Kotlin, everything that decides: timeline order, grid
  entries and headers per zoom level, date labels, which item the floating
  date names, the film-strip window, where the detail view lands when its
  item disappears, and how the info panel words a photo's metadata. Fully
  testable on the JVM.
- **app** — everything that touches the system: the MediaStore query and
  observer, thumbnails through MediaStore's own cache, EXIF and video
  metadata, the geocoder, share, trash and map intents, the permission gate,
  and the Compose screens.

## License

[GPL-3.0](LICENSE)
