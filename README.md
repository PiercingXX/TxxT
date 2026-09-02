# TxxT
> An SMS client whose defaults are the product.

A private SMS client for Android. Holds the system SMS role on a Pixel 9 Pro
running GrapheneOS. Text-first, AMOLED black, Space Mono / JetBrains Mono, same
stack as the rest of the phone suite. The features are ordinary; what makes it
worth building is which of them ship switched off. Photos go as MMS (scrubbed
on send, fetched on arrival, shown as `[Photo]` until tapped). The body face is JetBrains Mono Nerd Font Mono, so
the compose palette is monochrome Nerd glyphs rather than colour emoji.

## Privacy posture — stated as fact 🔒

There is no analytics and no crash reporting. No telemetry, no tracking pixel,
no ads, no Play Services, no link preview, no network contact enrichment.
Nothing is added to a message on its way out and nothing is phoned in about how
the app is used.

Messages themselves travel over the carrier network, because that is what
sending a text is. The narrower, checkable commitment: TxxT contacts no service
of its own, and its permission list is exactly the set the code uses.
`scripts/verify_privacy_claims.py` dumps the built APK's permissions and fails
on drift **in either direction** — one that arrives through a library's manifest
merge, or one that outlives the feature it was added for.

**Local-first.** Messages, contacts and settings live on the device. Theme sync
with XX-Launcher is a local broadcast. Backup is a local JSON export.

**Defaults with a spine.** Privacy leaks are opt-in, never opt-out. Read
receipts, typing indicators, delivery reports, MMS auto-download and
notification content preview are all **off by default**. RCS is excluded
entirely. Metadata — EXIF/XMP/IPTC on images, MP4/MOV atoms on video — is
scrubbed on every send, and the scrub is not optional. Voice messages are never
sent and never received; the thread screen exposes no mic.

Contact names come from the system contacts provider via `PhoneLookup`.
`READ_CONTACTS` is read-only, and declining it is a supported end state:
numbers, never blank rows.

## The compose bar ⌨️

Send is a monospace `➜` — a real codepoint from the bundled JetBrains Mono, not
a vector icon and not colour emoji. Filled buttons became borderless
bright-white type: a white pill on AMOLED black is a flashlight, a white word
is a label. Attach is `⊕`. Emoji from a small palette. No dictation mic.

## Status 🧪

Code-complete and installed. The SMS role is held, the UI works, the theme
broadcast lands, the notification sound plays — all verified on-device.

**Not proven against a live SIM.** Real send and receive over a carrier have not
been exercised; the delivery pipeline, the `SMS_DELIVER` path and MMS are
covered by JVM tests and nothing else. Treat the messaging path as untested
until a real message has made the round trip.

## Build 🛠️

JDK 21, JVM 17 bytecode. AGP 8.9.1, Kotlin 2.1.20, Gradle 8.11.1.
`compileSdk`/`targetSdk` 35, `minSdk` 24.

```
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug :core:test :app:testDebugUnitTest
```

778 JVM tests, green. `core/` holds the message and state-machine domain with
zero `android.*` imports, so the logic that has to be correct is testable
without a device; `scripts/verify_no_android_imports.py` enforces that.

## Docs 📚

- `docs/PRIVACY.md` — the privacy & notification design authority
- `docs/DESIGN.md` — architecture and the brand mapping
- `docs/FEATURES.md` — feature list
- `scripts/` — the verifiers behind every claim above
