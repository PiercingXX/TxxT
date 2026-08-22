# TxxT

A private SMS/MMS client for Android. Sideloaded on Pixel 9 / GrapheneOS.

## Privacy posture — stated as fact

TxxT has **no `INTERNET` permission**. The manifest does not declare it, so
the app cannot reach a network. That is machine-checkable:

```
aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk
```

There is no analytics and no crash reporting. There is no telemetry, no
tracking pixel, no remote fetch, no link preview, no network contact
enrichment. Nothing leaves the device.

**Local-first.** Messages, contacts, and settings live on the device. Theme
sync with the xx-launcher is on-device. Backup is a local JSON export.

**Defaults with a spine.** Privacy leaks are opt-in, never opt-out. Read
receipts, typing indicators, delivery reports, MMS auto-download, and
notification content preview are all **off by default**. RCS is excluded
entirely. Metadata (EXIF/XMP/IPTC on images, MP4/MOV atoms on video) is
scrubbed on every send — the scrub is not optional. Voice messages are never
sent and never received.

## Build

```
./gradlew assembleDebug testDebugUnitTest
```

## Docs

- `docs/PRIVACY.md` — the privacy & notification design authority
- `docs/DESIGN.md` — architecture
- `docs/FEATURES.md` — feature list