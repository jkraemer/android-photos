# Changelog

User-visible changes, in the spirit of [Keep a Changelog](https://keepachangelog.com).

## [Unreleased]

### Fixed

- A tap on a playing video hides the top bar and film strip along with the
  player's controls, as a tap on a photo does; they come back together too.

## [0.1.0] - 2026-10-27

### Added

- Library grid of the camera folder (`DCIM/Camera/`) only, photos and videos,
  oldest at the top and newest at the bottom; the app opens on the newest.
- Pinch between four zoom levels (3, 5, 15 and 30 columns) without losing
  your place: a floating day or month label at 3 and 5 columns, month and
  year headlines ("Oct 2026" with the year subdued, "2026") at 15 and 30,
  laid over the photos, that stay pinned at the top while their month or
  year scrolls by. Landscape keeps the thumbnail size.
- New photos appear while the app is open.
- Detail view: swipe between items (older to the left), pinch or double-tap
  to zoom, tap to hide everything but the photo, swipe down to go back.
- Film strip under the photo that moves along as you swipe; fling it to
  scrub through the timeline, tap a thumbnail to jump.
- Share through the Android share sheet and move to the system trash.
- Inline video playback with sound.
- Swipe up on a photo or video for its details: date, file, camera, lens,
  size, exposure and where it was taken, as a place name from the system
  geocoder (GrapheneOS: Settings → Location → Location services → Geocoder)
  or as coordinates. Tap the place to open it in a maps app. The app asks
  for access to photo locations the first time you swipe up.
