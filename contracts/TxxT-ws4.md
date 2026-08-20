<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS4 — Core: metadata scrubbing (task contract)

Scope: the task contract for **WS4 — Core: metadata scrubbing** of the workstream
inventory at `contracts/TxxT.md:101`. It builds the pure-Kotlin metadata scrubber
in `core/` with zero `android.*` imports that strips EXIF / XMP / IPTC from images
and GPS/device/creation-time/encoder atoms from MP4/MOV video
(`docs/PRIVACY.md:69`), applied before an attachment is added to the draft and
before it is sent, with **no re-encode by default** and **no "send with metadata"
toggle** — so the scrub logic that must be correct is JVM-testable without a
device.

The goal is **not stale and not started** on this branch
(`laundry-bot/queue-TxxT-ws2-corrective`, HEAD `a0a24c7`). Measured this session:
the `core/` module exists and is green (75 tests, 0 failures), but there is **no
metadata-scrubber code anywhere in it** — searches for `exif`, `scrub`, and
`metadata` across the tree return only prose in `contracts/TxxT.md`,
`docs/PRIVACY.md`, `docs/DESIGN.md`, and `docs/FEATURES.md`, and zero source
matches under `core/`. Every deliverable the WS4 goal names is **not started**;
this contract scopes the full build of the scrubber.

Authoritative sources read this session: `contracts/TxxT.md:101-112` (the WS4
goal and `done when`), `docs/PRIVACY.md:69-82` (the metadata-scrubbing section),
the existing `core/` sources and tests measured on disk, and the offline Gradle
dependency cache (measured: **no** EXIF / MP4 / metadata-parsing library is
available, so the scrubber must be hand-rolled binary parsing in pure Kotlin).

## State of the tree (measured this session)

Measured on branch `laundry-bot/queue-TxxT-ws2-corrective` (HEAD `a0a24c7`),
working tree clean. The `core/` module is fully scaffolded and green from WS2/WS3,
but contains **no metadata-scrubber code**. Every deliverable the WS4 goal names
is **not started**; the goal is not stale.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| Pure-Kotlin `core/` module wired into the Gradle build | **Already done (WS2)** | `settings.gradle:18` includes `':core'`; `core/build.gradle:1-21` applies `org.jetbrains.kotlin.jvm`, JVM target 1.8, JUnit 4.13.2 |
| Image scrubber strips EXIF / XMP / IPTC | **Not started** | search for `exif` across the tree returns only prose (`contracts/TxxT.md`, `docs/PRIVACY.md`, `docs/FEATURES.md`); zero source matches under `core/` |
| Video scrubber strips GPS/device/creation-time/encoder atoms from MP4/MOV | **Not started** | search for `metadata` and `scrub` under `core/` returns zero source matches; the requirement is at `docs/PRIVACY.md:73-76` |
| Applied before an attachment is added to the draft and before it is sent | **Not started (out of WS4 core scope)** | the pure scrubber API is not built; the on-device wiring into the send pipeline is WS7's domain (`contracts/TxxT.md:140`, `docs/PRIVACY.md:71`) and the attachment UI is WS11's (`contracts/TxxT.md:197`) — deferred, see below |
| No re-encode by default | **Not started** | no scrubber exists to preserve media bytes; the requirement is at `docs/PRIVACY.md:79-81` |
| No "send with metadata" toggle | **Not started** | no scrubber API exists; the requirement is at `docs/PRIVACY.md:82` (the scrub is not optional) |
| JVM suite feeds sample image/video payloads with GPS/camera/timestamp/encoder metadata and proves containers gone, media not re-encoded | **Not started** | the 7 existing test files cover the WS2/WS3 domain only (see gate report); no scrubber test exists |

**Gate report (run this session).** `./gradlew :core:test --offline --rerun-tasks`
→ **BUILD SUCCESSFUL in 2s**; `:core:test` executed fresh. Test report
(`core/build/test-results/test/*.xml`, this session): ConversationListTest 7,
MessageModelTest 10, PinSortArchiveTest 10, ReceiveStateTest 16,
ScheduleDelayTest 14, SendStateMachineTest 13, UnreadCountTest 5 — **75 tests, 0
failures, 0 errors, 0 skipped** across 7 test files. The no-`android.*` gate
(`python3 scripts/verify_no_android_imports.py`) exits 0 this session: "no
android.* imports in 15 Kotlin source files under core/". These 75 tests cover the
WS2/WS3 domain only; none exercise metadata scrubbing, so the WS4 `done when` is
unproven until the new tests land.

**Toolchain note (measured this session).** The interactive shell here did **not**
have `JAVA_HOME` set, so the gate above was run with the env prefix
`JAVA_HOME=/home/piercingxx/.local/android-toolchain/jdk17` (the jdk17 from the
measured toolchain at `/home/piercingxx/.local/android-toolchain/`). The task
guarantee is that JAVA_HOME (jdk17) and ANDROID_HOME are injected into every
verify at execution time, so the bare verify lines below rely on that injection —
the same pattern the WS3 contract uses.

**Dependency-cache finding (measured this session).** The offline Gradle cache
under `/home/piercingxx/.gradle/caches/modules-2/files-2.1/` contains **no**
EXIF / MP4 / metadata-parsing library (searched for `exif`, `metadata`, `mp4`,
`isoparser`, `jcodec`, `thumbnailator` — zero matches). `core/build.gradle:19-21`
declares only `testImplementation 'junit:junit:4.13.2'`. So the WS4 scrubber
cannot pull in a third-party metadata library offline; it must be **hand-rolled
binary parsing in pure Kotlin** — a JPEG segment walker for images and an MP4 atom
walker for video. This is consistent with the goal's "pure-Kotlin" requirement and
keeps the module dependency-clean.

## Deferred verification (the box cannot prove these)

- **The on-device wiring ("applied before an attachment is added to the draft and
  before it is sent").** `docs/PRIVACY.md:71` says any image/video leaving the
  device is scrubbed before it is attached, and the goal names that application
  point. But WS4 is a **pure-core** workstream: it proves the scrubber exists and
  strips the metadata containers, not the Android wiring that calls it at
  attach/send time. That wiring is WS7's send pipeline (`contracts/TxxT.md:140`)
  and WS11's attachment UI (`contracts/TxxT.md:197`), both later workstreams. WS4
  proves the scrubber is *available and correct* in `core/`; the on-device
  attach/send hookup is the later workstreams' scope, exactly as WS3's blocking
  wiring was deferred to WS9.
- **Real-world media compatibility.** The tests feed *synthetic* JPEG and MP4/MOV
  payloads constructed in Kotlin (minimal but structurally valid containers with
  the metadata embedded), because no real media fixtures exist in the tree and no
  parsing library is available offline. Whether the hand-rolled parsers also strip
  metadata from *every* real-world JPEG/MP4 variant (progressive JPEG, fragmented
  MP4, vendor-specific atoms) is a breadth the synthetic suite cannot exhaustively
  prove; the box proves the containers the goal names (EXIF/XMP/IPTC segments;
  GPS/device/creation-time/encoder atoms) are stripped from structurally valid
  samples. Real-device media coverage is the operator's on-device check.
- **The "no re-encode" claim's quality semantics.** The box proves the media bytes
  (image scan data, video `mdat`) are byte-identical after scrubbing — i.e. the
  scrubber performs no re-encode. Whether the stripped output *decodes and
  displays* identically in every player is an on-device rendering check outside
  the JVM suite.

## Tasks

### T1 — Image scrubber: strip EXIF / XMP / IPTC segments

Build the image metadata scrubber in `core/` — a pure-Kotlin type with zero
`android.*` imports, matching the style of the existing `core/` model (see
`ConversationFlags.kt:20-50`). It parses a JPEG byte stream and strips the
metadata-bearing APP segments: APP1 carrying EXIF and XMP, and APP13 carrying
IPTC, removing the GPS, camera make/model, timestamp, software, and thumbnail
residue those segments hold (`docs/PRIVACY.md:73-74`), while preserving the image
data segments (SOI, SOF, SOS, and the scan data) unchanged. The test file
`ImageMetadataScrubberTest.kt` constructs a synthetic JPEG in Kotlin whose APP1
segment carries EXIF GPS/camera/timestamp fields and an APP13 segment carries IPTC
caption data, feeds it through the scrubber, and asserts the EXIF, XMP, and IPTC
segments are gone from the output while the image data segments remain. This task
owns the image-side container stripping only; the video-side atom stripping is T2,
and the no-re-encode byte-preservation proof is T3 — each with its own test file
so the verifies cannot be confused.

- verify: ./gradlew :core:test --offline --tests com.piercingxx.txxt.core.ImageMetadataScrubberTest
- files: core/src/main/kotlin/com/piercingxx/txxt/core/ImageMetadataScrubber.kt, core/src/test/kotlin/com/piercingxx/txxt/core/ImageMetadataScrubberTest.kt

### T2 — Video scrubber: strip metadata atoms from MP4/MOV

Build the video metadata scrubber in `core/` — a pure-Kotlin type with zero
`android.*` imports. It parses the MP4/MOV atom (box) tree and strips the
metadata-bearing atoms: GPS, device model, creation time, and encoder
(`docs/PRIVACY.md:75-76`) — the `moov.udta` user-data and `moov.meta` metadata
atoms that carry GPS/device/creation-time/encoder values — while preserving the
`mdat` media-data atom and the container structure the media needs. The test file
`VideoMetadataScrubberTest.kt` constructs a synthetic MP4/MOV in Kotlin whose
`moov` subtree carries GPS, device-model, creation-time, and encoder metadata
atoms plus a `mdat` atom, feeds it through the scrubber, and asserts the metadata
atoms are gone while the `mdat` media data remains. This task owns the video-side
atom stripping only; the image-side segment stripping is T1, and the no-re-encode
byte-preservation proof is T3 — each with its own test file so the verifies cannot
be confused.

- verify: ./gradlew :core:test --offline --tests com.piercingxx.txxt.core.VideoMetadataScrubberTest
- files: core/src/main/kotlin/com/piercingxx/txxt/core/VideoMetadataScrubber.kt, core/src/test/kotlin/com/piercingxx/txxt/core/VideoMetadataScrubberTest.kt

### T3 — No re-encode by default: media bytes preserved

Build the no-re-encode guarantee in `core/` — the property that the scrubber
removes metadata **containers only** and never re-encodes the media itself
(`docs/PRIVACY.md:79-81`: "No re-encode by default — re-encoding changes quality
and is expensive. Strip the metadata containers"). The scrubber's output must
carry the image scan data and the video `mdat` media data **byte-identical** to
the input; only the metadata segments/atoms are removed. The test file
`NoReencodeTest.kt` feeds both a synthetic JPEG and a synthetic MP4/MOV through
the respective scrubbers and asserts, for each, that the media payload bytes
(image scan data / `mdat` content) are exactly preserved in the output while the
metadata containers are gone — proving a strip, not a re-encode. This task owns
the byte-preservation property only; the strip behaviour itself is T1's and T2's,
each with its own test file so the verifies cannot be confused.

- verify: ./gradlew :core:test --offline --tests com.piercingxx.txxt.core.NoReencodeTest
- files: core/src/test/kotlin/com/piercingxx/txxt/core/NoReencodeTest.kt

### T4 — No "send with metadata" toggle: no metadata-preserving path

Build the no-toggle guarantee in `core/` — the property that the scrubber API
exposes **only** the scrubbing path and no mode, flag, or overload that keeps
metadata (`docs/PRIVACY.md:82`: "The scrub is not optional — there is no 'send
with metadata' toggle"). The scrubber entry point must be a single, unconditional
strip: there is no `keepMetadata`, no `preserveMetadata`, no boolean that skips
the scrub. The test file `ScrubberApiTest.kt` asserts the scrubber's public API
surface contains no metadata-preserving switch and that calling the single entry
point on a synthetic JPEG and MP4/MOV always strips the metadata containers (never
returns them untouched). This task owns the API-surface / no-toggle guarantee
only; the strip behaviour itself is T1's and T2's, each with its own test file so
the verifies cannot be confused.

- verify: ./gradlew :core:test --offline --tests com.piercingxx.txxt.core.ScrubberApiTest
- files: core/src/main/kotlin/com/piercingxx/txxt/core/MetadataScrubber.kt, core/src/test/kotlin/com/piercingxx/txxt/core/ScrubberApiTest.kt

## Final gate

The whole-workstream gate is the repo's own `core/` test command — it compiles the
module and runs the full JVM suite (the WS2/WS3 75 tests plus the new WS4 scrubber
tests):

- ./gradlew :core:test --offline

It must exit 0 (75+ tests, 0 failures), and the existing no-`android.*` gate
(`python3 scripts/verify_no_android_imports.py`, which already exits 0 over the 15
existing `core/` sources and must continue to pass over the new scrubber sources)
keeps the "no `android.*` imports" claim true. No individual task claims this
command as its verify; each task's verify is its own single-node test selector,
and the Final gate confirms the whole suite holds together with the new scrubber
tests included.