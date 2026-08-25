# TxxT

A private SMS/MMS client for Android. Sideloaded on Pixel / GrapheneOS.


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
