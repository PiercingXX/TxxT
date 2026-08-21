<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

<!-- design-digest: 2a3eea8841347937 -->

# TxxT — Workstream Inventory

Scope: the full remaining build of TxxT, a Google-free, sideloaded SMS/MMS app
for a GrapheneOS Pixel 9. This is the **workstream inventory** — the list of
every workstream needed to finish the project, in build order. It is **not** a
task contract: no task, no verify, no per-workstream gate lives here. Each
workstream gets its own verify-sectioned contract later, when its turn comes.

Authoritative sources read this session: `docs/DESIGN.md`, `docs/FEATURES.md`,
`docs/PRIVACY.md`, `docs/INSPIRATION.md`, `docs/RESEARCH.md`. The privacy
posture in `docs/PRIVACY.md` is the authority for how every screen behaves.

## State of the tree (measured this session)

Measured on branch `main` (HEAD `03047a5`), clean working tree. The project is
in the **design phase only** — there is no Android project, no build system, no
source code, and no test suite. The only deliverables present are the five
design documents under `docs/`.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| Open-source SMS research | **Already done** | `docs/RESEARCH.md:1` — research phase, scope declared "no build" |
| Compiled feature list & open questions | **Already done** | `docs/FEATURES.md:3` — "Compiled feature list"; resolved answers at `docs/FEATURES.md:106` |
| Design direction (aesthetic, principles, screens, stack) | **Already done** | `docs/DESIGN.md:1` — hybrid defaulted at `docs/DESIGN.md:11`; stack at `docs/DESIGN.md:82` |
| Brand-system inheritance | **Already done** | `docs/INSPIRATION.md:1` — tokens, type, mark, cleanroom, no-`INTERNET` claim, package layout |
| Privacy & notification posture | **Already done** | `docs/PRIVACY.md:1` — the authority; §1 receipts/RCS, §2 no bubbles, §3 redaction, §4 scrubbing, §5 voice, §6 starred, §7 theme-sync |
| Android project scaffold | **Not started** | no `settings.gradle.kts`, no `app/`, no `build.gradle*` anywhere in the tree (tree root holds only `docs/` and `.gitignore`) |
| Build system / Gradle wrapper | **Not started** | no `gradlew`, no `gradle/` directory |
| Any source code | **Not started** | the only tracked files are `docs/*.md` and `.gitignore` |
| Any tests / runnable gate | **Not started** | no test directories, no test command exists |

**Gate report.** There is no runnable gate in this tree this session: no Gradle
project, no test suite, no CI command — nothing to execute, so no real numbers
were produced. The project's eventual whole-project gate (per the sibling-repo
convention in `Nope-Mode`/`xx-vitals`) is the Android build-and-unit-test
command plus an `aapt2 dump permissions` check for the no-`INTERNET` claim, but
neither exists yet because WS1 (the scaffold) has not landed. Until WS1 builds
the project, every workstream's `done when` is uncheckable by this box. The
measured state is: **0 build files, 0 source files, 0 tests, 0 runnable gates.**

**Deferred by the operator (not build workstreams):** the Nagatha cleanroom of
the best options (`docs/FEATURES.md:132`, `docs/RESEARCH.md:94`) and the
theme-sync channel confirmation, which needs the xx-launcher's source
(`docs/PRIVACY.md:133`, `docs/FEATURES.md:133`). WS14 carries the theme-sync
design but its launcher-side contract stays a proposal until that source is
readable.

## Workstreams

Build order. Each workstream's `done when` is concrete and checkable; the
pure-Kotlin core (WS2–WS5) is deliberately first after the scaffold because it
is JVM-testable without a device, matching the stack posture in
`docs/INSPIRATION.md:137` and `docs/DESIGN.md:87`.

### WS1 — Project scaffold & build

goal: Stand up the Android Gradle project for `com.piercingxx.txxt` — Kotlin,
Views + viewBinding, Room + Gson, minSdk 24, target GrapheneOS Pixel 9 — with
the manifest declaring no `INTERNET` permission, the default-SMS-handler role,
`POST_NOTIFICATIONS`, and `FLAG_SECURE`-ready activities, plus the underlined-XX
adaptive icon and the local toolchain wired (a `local.properties` pointing at
the Android SDK, or the `ANDROID_HOME` environment variable, per the sibling-repo
convention) so `./gradlew assembleDebug` produces an installable APK.

done when: `./gradlew assembleDebug` exits 0 and `app/build/outputs/apk/debug/app-debug.apk`
exists; `aapt2 dump permissions` on that APK lists no `android.permission.INTERNET`;
the manifest declares the default-SMS-handler role and `POST_NOTIFICATIONS`; the
adaptive icon renders the underlined-XX logomark on Ink.

### WS2 — Core: message model & state machine

goal: Build the pure-Kotlin message domain in `core/` with zero `android.*`
imports — the SMS/MMS message and conversation/thread model, send/receive state,
pending/delayed/scheduled-message states, unread counts, and the pinning /
sorting / archiving flags — so the logic that must be correct is JVM-testable
without a device.

done when: the `core/` module compiles with no `android.*` imports (checked by
grep over `core/src/`), and a JVM unit-test suite covers the state machine —
sending, receiving, scheduling/delaying, unread-count derivation, and
pin/sort/archive transitions — and passes.

### WS3 — Core: blocking filters & starred call-through

goal: Build the pure-Kotlin blocking domain in `core/` — `BlockingFilter`,
keyword/phrase matching, unknown-sender rule, the blocklist model, and the
starred-contacts bypass that lets a starred contact through every suppression
(`docs/PRIVACY.md:106`), with a block rule that would match a starred contact
surfaced with a reason rather than applied silently (`docs/INSPIRATION.md:162`).

done when: a JVM unit-test suite proves keyword and unknown-sender filters
match and reject correctly, proves starred contacts bypass every suppression
while unstarred keep the full posture, and proves a rule matching a starred
contact surfaces a reason instead of applying — all green, all in `core/` with
no `android.*` imports.

### WS4 — Core: metadata scrubbing

goal: Build the pure-Kotlin metadata scrubber in `core/` that strips EXIF / XMP
/ IPTC from images and GPS/device/creation-time/encoder atoms from MP4/MOV
video, applied before an attachment is added to the draft and before it is sent
(`docs/PRIVACY.md:69`), with no re-encode by default and no "send with
metadata" toggle.

done when: a JVM unit-test suite feeds sample image and video payloads carrying
GPS, camera, timestamp, and encoder metadata through the scrubber and proves
those containers are gone while the media itself is not re-encoded — all green,
in `core/` with no `android.*` imports.

### WS5 — Core: backup serialization

goal: Build the pure-Kotlin JSON backup model in `core/` matching the launcher's
JSON conventions (`docs/INSPIRATION.md:132`), covering messages plus
settings/blocklist/starred (`docs/PRIVACY.md:110`), with export serialization,
import deserialization, validation (version and value-range checks), and
idempotent re-import so a re-import is a no-op (`docs/INSPIRATION.md:164`).

done when: a JVM unit-test suite proves a round-trip export→import reproduces
the messages/settings/blocklist/starred set, proves a newer-version or
out-of-range payload is rejected rather than partially imported, and proves a
second import of the same payload is a no-op — all green, in `core/` with no
`android.*` imports.

### WS6 — Data layer (Room + Gson)

goal: Write the Android data layer in `data/` — the Room entities, DAOs,
`TxxTDatabase`, the mappers between the pure `core/` model and Room storage, the
starred-contact flag persisted, and the schema exported and committed — wired to
Gson for the backup JSON (`docs/INSPIRATION.md:132`, `docs/DESIGN.md:86`).

done when: the `data/` classes compile against the `core/` model, the Room
schema is exported and committed, and the pure mapper slice between the core
model and storage has a JVM unit test that passes; the Room wiring itself is
device-verified (see Deferred).

### WS7 — Service layer: SMS/MMS send & receive

goal: Write the service layer in `service/` — `SmsReceiver`, `MmsReceiver`, and
the send pipeline — that sends SMS/MMS, never requests delivery or read reports
(`docs/PRIVACY.md:23`), keeps MMS auto-download off so remote content is fetched
only on explicit tap (`docs/PRIVACY.md:151`), drops inbound audio MMS at the
inbox boundary without storing it, and sends the optional auto-reply SMS (off by
default, per-contact overridable) telling the sender voice messages aren't
accepted (`docs/PRIVACY.md:91`).

done when: the `service/` classes compile, and on-device checks confirm a real
send and receive work, no delivery/read-report request is made, MMS is not
auto-downloaded, an inbound audio MMS is dropped un-stored, and the auto-reply
SMS fires only when enabled.

### WS7a — Wire the SMS/MMS receivers into the app's execution flow

goal: WS7 built `SmsReceiver`, `MmsReceiver` and the send pipeline, and the
audit found them to be DEAD CODE: declared in `AndroidManifest.xml` but never
reached from the running application, so no inbound SMS or MMS is ever handled.
Wire them in — registration/dispatch reaches `SmsReceiver.onReceive` and
`MmsReceiver.onReceive` for a real inbound intent, those handlers route through
the existing `ReceivePolicy` and send pipeline, and the privacy behaviour WS7
already implements (no delivery/read reports, MMS auto-download off, inbound
audio MMS dropped un-stored, auto-reply only when enabled) holds on that live
path. Do NOT re-extract or rename anything: WS7's policy classes and their
tests are correct and already delivered — the gap is only that nothing calls
them.

done when: a JVM unit test drives an inbound SMS intent and an inbound
audio-MMS intent through the app's registered receiver entry points and asserts
the resulting behaviour (auto-reply decision honoured, audio MMS dropped
un-stored), such that the test FAILS if the receivers are never invoked; and a
test asserts the manifest-declared receiver class names resolve to those
classes. `./gradlew :app:testDebugUnitTest --offline` passes. Compilation is
NOT evidence here — WS7 compiled green while the receivers were unreachable.

### WS8 — Notification service

goal: Write `NotificationService` in `service/` that posts notifications
showing **sender name only**, never message content (`docs/PRIVACY.md:60`),
sets `FLAG_SECURE` on the thread and conversation-list activities
(`docs/PRIVACY.md:64`, `:160`), never uses notification bubbles or chat-heads —
no `BUBBLE_DATA` / `FLAG_BUBBLE`, no overlay permission (`docs/PRIVACY.md:51`) —
supports per-contact silent/vibrate/sound/redacted control defaulting to the
global posture, makes starred contacts always notify, and supports quick reply.

done when: the notification code compiles, and on-device checks confirm the
shade shows sender-name-only with no content preview, no bubble/chat-head is
ever posted, `FLAG_SECURE` blocks recents preview and screenshots, starred
contacts always notify while unstarred follow the redacted/suppressed posture,
and quick reply works.

### WS9 — Blocking service wiring

goal: Write the `block/` package that applies the WS3 filters to inbound
messages — routing messages from unknown senders to a quarantine view rather
than the main thread list unless starred (`docs/PRIVACY.md:164`), applying
keyword/phrase and unknown-sender blocking, and surfacing any block that would
match a starred contact with a reason and an override (`docs/INSPIRATION.md:162`).

done when: the `block/` code compiles against the WS3 core, and on-device checks
confirm an unknown sender lands in quarantine, a matching keyword is blocked,
and a rule that would match a starred contact surfaces a reason and an override
instead of applying silently.

### WS10 — UI: conversation list

goal: Build the conversation-list screen in `ui/` with Views + viewBinding —
text-first rows of contact name (Space Mono), preview line, and timestamp, no
avatar tiles by default with an optional monogram, swipe-to-archive /
swipe-to-delete / swipe-to-call / swipe-to-schedule actions, and search reachable
via gesture or keystroke, all on AMOLED black with the one-accent rule
(`docs/DESIGN.md:59`, `docs/INSPIRATION.md:186`).

done when: the screen renders per the design on-device — text-first rows on
AMOLED black, tabular-figure timestamps, swipe actions fire, search is
reachable — with no icon grid and at most one accent per screen.

### WS11 — UI: conversation thread

goal: Build the thread screen in `ui/` with Views + viewBinding — messages as
**text-first lines, never chat bubbles** (`docs/PRIVACY.md:41`), inbound left /
outbound right with minimal chrome, timestamps in Space Mono, a compose bar with
a minimal attachment affordance and **no voice-message affordance**
(`docs/PRIVACY.md:88`), and quick reply from the notification.

done when: the thread renders on-device as plain text lines on AMOLED black with
no bubble cards and no voice affordance, sent/received alignment and the
white-opacity hierarchy match `docs/INSPIRATION.md:186`, and quick reply works.

### WS12 — UI: settings

goal: Build the settings screen in `ui/` with Views + viewBinding — the seven
theme presets, font toggle, backup/restore, blocking management, lock-screen
privacy options (sender-only / content / nothing, defaulting to sender-only),
notification preferences, the starred-contacts list, and the theme auto-sync
toggle (`docs/DESIGN.md:75`, `docs/PRIVACY.md:62`, `docs/PRIVACY.md:141`).

done when: the screen renders on-device and every setting persists and takes
effect — theme preset applies, lock-screen privacy defaults to sender-only,
starred contacts are editable and exported/imported with backup, and a manual
in-app theme overrides auto-sync.

### WS13 — Accessibility

goal: Add in-app speech-to-text and in-app text-to-speech so the app is usable
without typing and without reading the screen (`docs/FEATURES.md:38`), working
with any IME including Gboard (`docs/DESIGN.md:93`).

done when: on-device checks confirm dictation inserts into the compose field and
text-to-speech reads a message aloud, both without any network dependency.

### WS14 — Theme system & xx-launcher auto-sync

goal: Implement the seven named theme presets (AMOLED Night, Graphite, Forest
Night, Ocean Drift, Burgundy, Paper, Mist — `docs/FEATURES.md:59`) and the
theme auto-sync with the xx-launcher: TxxT subscribes to the launcher's active
theme and re-applies it, local-only with no `INTERNET` permission, and a manual
in-app theme wins over auto-sync (`docs/PRIVACY.md:125`).

done when: all seven presets render correctly on-device, and — once the
xx-launcher's actual theme-publish mechanism is confirmed from its source —
auto-sync follows the launcher's theme change on-device without adding
`INTERNET`; the launcher-side contract is a proposal until that source is
readable (`docs/PRIVACY.md:133`).

### WS15 — Failure-mode hardening

goal: Handle every row of the failure-mode table (`docs/INSPIRATION.md:158`) —
say so on first launch if SMS permission is denied and never silently fail to
send; reconcile unread counts and pending sends across a reboot; surface a block
that matches a legit contact with a reason and override; retry a failed MMS
download with backoff and show the failed state; make backup restore idempotent;
and warn loudly if the default-SMS-handler role is revoked.

done when: each failure-mode behaviour is implemented and on-device checks
confirm the required behaviour for SMS-permission-denied, reboot reconcile,
blocked-legit-contact override, MMS-download retry/failure display, idempotent
restore, and default-handler-revoked warning.

### WS16 — Packaging & privacy verification

goal: Produce the final sideloaded APK and verify the privacy claims: no
`INTERNET` permission (machine-checkable via `aapt2 dump permissions`,
`docs/INSPIRATION.md:117`), no analytics/crash reporting, and a README in the
dry maker register stating the privacy posture as fact
(`docs/INSPIRATION.md:86`).

done when: the full build-and-unit-test gate exits 0 on a clean checkout and the
APK is produced, `aapt2 dump permissions` shows no `INTERNET`, and the README
states the no-`INTERNET` / no-analytics / local-first posture as fact.

## Final gate

The whole-project gate is the repo's own build-and-test command, which does not
exist yet in this tree — WS1 must create it. Once the scaffold lands, the gate
is, per the sibling-repo convention:

- ./gradlew assembleDebug testDebugUnitTest

plus the no-`INTERNET` privacy check:

- aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk

Neither command can run this session because the tree has no Gradle project
(measured: 0 build files). No individual workstream's `done when` claims this
gate; it is the whole-project gate that WS16 closes. Until WS1 exists, every
`done when` above is uncheckable by this box and the on-device items are the
operator's.