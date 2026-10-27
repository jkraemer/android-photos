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
falls back to coordinates; tap it to open a maps app. The app's own code
opens no connections; it holds the internet permission only because the
system geocoder requires it, so turning the app's network access off just
leaves coordinates.

**Text** — in a photo's info panel, "Recognise text" reads the text in the
photo (German and English, with Tesseract, on the phone) and shows it there:
select and copy parts, copy all of it, or tap a phone number, email address
or link to open the dialer, mail app or browser.

## Requirements

- Android 15+ (minSdk 35)
- Full access to photos and videos ("Allow all"); with "Select photos" the
  app asks you to change that in its settings.
- Access to photo locations, asked for when you first open the info panel;
  without it the panel shows everything but the place.
- A 64-bit ARM phone (`arm64-v8a`): the APK carries Tesseract's native code for nothing else.
- Developed against a Pixel on GrapheneOS with Google Camera.

## Building

JDK 21 and the Android SDK, then (the first build downloads Tesseract's
German and English models, about 24 MB, from `tesseract-ocr/tessdata_best`
at a pinned commit and checks their SHA-256 sums):

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
  item disappears, how the info panel words a photo's metadata, and which
  parts of recognised text are phone numbers, emails or links. Fully
  testable on the JVM.
- **app** — everything that touches the system: the MediaStore query and
  observer, thumbnails through MediaStore's own cache, EXIF and video
  metadata, the geocoder, text recognition with Tesseract, share, trash, map
  and link intents, the permission gate, and the Compose screens.

## Verifying releases

Release APKs are signed with a certificate whose SHA-256 fingerprint is:

```
7ce1acf13e343f199458e89f34ef68b605fc0cf92a58a79c476ce28395a79d3e
```

Check a downloaded APK with

```sh
apksigner verify --print-certs android-photos-*.apk
```

or on-device with [AppVerifier](https://github.com/soupslurpr/AppVerifier)
(package `net.jkraemer.photos`).

## License

[GPL-3.0](LICENSE)
