# TxxT
> SMS that can't phone home — the manifest is the proof, not the marketing.

A private SMS/MMS client for Android. Holds the system SMS role on a Pixel 6
running GrapheneOS. Text-first, AMOLED black, Space Mono / JetBrains Mono, same
stack and conventions as the rest of the phone suite.

There is no screenshot in this repo and there won't be one. Every activity sets
`FLAG_SECURE` (`docs/PRIVACY.md` §3), so `screencap` returns a black rectangle.
scripts > screenshots anyway — `scripts/verify_*.py` prove more than a picture
would.

## Privacy posture — stated as fact 🔒

TxxT has **no `INTERNET` permission**. The manifest does not declare it, so the
app cannot reach a network. That is machine-checkable:

```
aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk
```

`scripts/verify_privacy_claims.py` runs exactly that command and fails if the
permission ever appears.

There is no analytics and no crash reporting. No telemetry, no tracking pixel,
no remote fetch, no link preview, no network contact enrichment. Nothing leaves
the device.

**Local-first.** Messages, contacts, and settings live on the device. Theme sync
with XX-Launcher is a local broadcast. Backup is a local JSON export
(`data/BackupJson.kt`, `data/RestoreService.kt`) restored from Settings.

**Defaults with a spine.** Privacy leaks are opt-in, never opt-out. Read
receipts, typing indicators, delivery reports, MMS auto-download, and
notification content preview are all **off by default**. RCS is excluded
entirely. Metadata (EXIF/XMP/IPTC on images, MP4/MOV atoms on video) is scrubbed
on every send — the scrub is not optional. Voice messages are never sent and
never received, and the thread screen exposes no mic.

## The compose bar ⌨️

Reworked, and shorter than it was. The dictation mic is gone. Send is a
monospace `➜` (U+279C), a codepoint the bundled JetBrains Mono actually covers —
no vector icon, no filled circle. Settings moved out of the compose row into a
top-right bar. Every hard-white filled button became borderless bright-white
type: `NEW` on the conversation list, the glyph on the thread screen. A white
pill on AMOLED black is a flashlight; a white word is a label.

The activity roots carry `fitsSystemWindows`, which is what fixed the top row
sitting under the status bar.

## Theme sync 🎨

XX-Launcher broadcasts `xx.launcher.THEME_CHANGED` carrying a theme name and the
resolved background ARGB. All nine family apps subscribe. TxxT's receiver
(`.theme.ThemeSyncReceiver`, exported) resolves the carried name to a
`ThemePreset`, persists it to the `txxt_theme` store so it survives process
death, and the applier repaints. Seven named grounds — AMOLED Night, Graphite,
Forest Night, Ocean Drift, Burgundy, Paper, Mist — plus the launcher's Custom,
which TxxT keys off the name and therefore leaves on the last resolved preset;
xx-phone is the app that consumes the raw ARGB. Manual beats ambient: pick a
theme in Settings and the launcher stops overriding it (`docs/PRIVACY.md` §7).

## Notifications 🔔

TxxT ships its own sound: `app/src/main/res/raw/txxt.wav` on channel
`txxt_messages_v2`, alongside vibrate-only and silent channels. The `_v2` is not
decoration — Android freezes a channel's sound at creation and refuses to change
it, so shipping a new tone means minting a successor id and deleting the
predecessor. Every future tone change bumps the version again.

## Status 🧪

Code-complete and installed. The SMS role is held, the UI works, the theme
broadcast lands, the notification sound plays — all verified on-device today.

**Not proven against a live SIM.** Real send and real receive over a carrier
have not been exercised. The delivery pipeline, the SMS_DELIVER path, and MMS
are covered by JVM tests and nothing else. Treat the messaging path as untested
until a real message has made the round trip.

## Build 🛠️

Built with JDK 21; the modules target JVM 17 bytecode. AGP 8.9.1, Kotlin 2.1.20,
Gradle 8.11.1. `compileSdk`/`targetSdk` 35, `minSdk` 24.

```
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug :core:test :app:testDebugUnitTest
```

Tests, run today, all green:

| Module | Tests |
|---|---|
| `:core` (pure JVM, zero `android.*` imports) | 248 |
| `:app` | 461 |
| **Total** | **709** |

`core/` holds the message and state-machine domain with no Android imports, so
the logic that has to be correct is testable without a device.
`scripts/verify_no_android_imports.py` enforces that.

## Docs 📚

- `docs/PRIVACY.md` — the privacy & notification design authority
- `docs/DESIGN.md` — architecture and the brand mapping
- `docs/FEATURES.md` — feature list
- `scripts/` — the verifiers behind every claim above
