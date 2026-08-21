<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS7 — Service layer: SMS/MMS send & receive (task contract)

Scope: the task contract for **WS7 — Service layer: SMS/MMS send & receive** of
the workstream inventory at `contracts/TxxT.md:140`. It writes the service layer
in `service/` — `SmsReceiver`, `MmsReceiver`, and the send pipeline — that sends
SMS/MMS, never requests delivery or read reports (`docs/PRIVACY.md:23`), keeps
MMS auto-download off so remote content is fetched only on explicit tap
(`docs/PRIVACY.md:151`), drops inbound audio MMS at the inbox boundary without
storing it, and sends the optional auto-reply SMS (off by default, per-contact
overridable) telling the sender voice messages aren't accepted
(`docs/PRIVACY.md:91`).

`done when`: the `service/` classes compile, and on-device checks confirm a real
send and receive work, no delivery/read-report request is made, MMS is not
auto-downloaded, an inbound audio MMS is dropped un-stored, and the auto-reply
SMS fires only when enabled.

Authoritative sources read this session: `contracts/TxxT.md:140-153` (the WS7
goal and `done when`), `docs/PRIVACY.md:23` (delivery/read reports OFF — "No
`SMS_DELIVERY_REPORT` / read-report requests"), `docs/PRIVACY.md:151` (MMS
auto-download OFF — "Remote MMS content is fetched only on explicit tap"),
`docs/PRIVACY.md:91-101` (voice messages never received — "an inbound MMS whose
attachment is audio is not downloaded and not stored. It is dropped at the inbox
boundary"; the auto-reply SMS is "off by default, per-contact overridable" and
reads *"Voice messages aren't accepted. Send text or a photo."*),
`docs/INSPIRATION.md:147` (package layout — `service/` holds `SmsReceiver`,
`MmsReceiver`, `NotificationService`), the existing `core/` model sources and
tests measured on disk, `app/src/main/AndroidManifest.xml` (the default-SMS-role
permission and the comment that "the broadcast-receiver components that complete
the role are WS7's scope"), and the offline Gradle dependency cache.

## State of the tree (measured this session)

Measured on branch `laundry-bot/queue-TxxT-ws5-i3` (HEAD `6a8241d`), clean
working tree. The pure `core/` model is fully built and green from WS2–WS5. The
`service/` layer is **not started** — there is no `service/` package, no
`SmsReceiver`, no `MmsReceiver`, no send pipeline, and no SMS/MMS runtime
permission in the manifest. Every deliverable the WS7 goal names is **not
started**; the goal is not stale.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| `SmsReceiver` | **Not started** | no `service/` package anywhere in the tree (measured: `find app/src -type f` returns only `AndroidManifest.xml` and the `res/` icon files; `git ls-tree -r main -- service` and the working tree both have no `service/` path) |
| `MmsReceiver` | **Not started** | no `MmsReceiver` class, no `service/` package |
| The send pipeline (sends SMS/MMS) | **Not started** | no send code exists; `app/` has no Kotlin source at all |
| Never requests delivery or read reports | **Not started** | no send code to carry the policy; `docs/PRIVACY.md:23` is the authority (OFF — no `SMS_DELIVERY_REPORT` / read-report requests) |
| MMS auto-download off (fetch only on explicit tap) | **Not started** | no MMS receive code; `docs/PRIVACY.md:151` is the authority |
| Drops inbound audio MMS un-stored at the inbox boundary | **Not started** | no receive code; `docs/PRIVACY.md:91-92` is the authority |
| Optional auto-reply SMS (off by default, per-contact overridable) | **Not started** | no auto-reply code; `docs/PRIVACY.md:96-97` is the authority |
| Manifest SMS/MMS runtime permissions + receiver registration | **Not started** | `app/src/main/AndroidManifest.xml:7` declares only `android.permission.role.SMS`; `:11` declares `POST_NOTIFICATIONS`; no `RECEIVE_SMS`/`RECEIVE_MMS`/`SEND_SMS`/`READ_SMS`/`WRITE_SMS` and no `<receiver>` elements (measured: `search_text` for `RECEIVE_SMS|SEND_SMS|RECEIVE_MMS|READ_SMS|WRITE_SMS` across `contracts/` returned 0 matches, and the manifest read this session has none) |
| The `service/` classes compile | **Not started** | `app/` has no Kotlin source (measured: `find app/src -type f`), so nothing to compile yet |

**Gate report (run this session).** The existing `core/` suite is green:
`./gradlew :core:test --offline` → **BUILD SUCCESSFUL in 845ms**. Test report
(`core/build/test-results/test/*.xml`, this session): BackupExportTest 8,
ConversationListTest 7, MessageModelTest 10, PinSortArchiveTest 10,
ReceiveStateTest 16, ScheduleDelayTest 14, SendStateMachineTest 13,
UnreadCountTest 5 — **83 tests, 0 failures, 0 errors** across 8 test files. The
`app/` module configures and its JVM unit-test task is runnable offline:
`./gradlew :app:testDebugUnitTest --offline --dry-run` → **BUILD SUCCESSFUL in
833ms**, task graph includes `:app:processDebugManifestForPackage`,
`:app:kaptDebugKotlin`, `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`,
`:app:testDebugUnitTest` (all SKIPPED only because no source exists yet). The
app module's declared dependencies (Room, Gson, coroutines, junit) are all
present in the offline cache, so once the `service/` sources land the
`:app:testDebugUnitTest` gate is expected to run green on this box.

**Toolchain note (measured this session).** The interactive shell here did not
have `JAVA_HOME`/`ANDROID_HOME` set, so the gates above were run with the env
prefix `JAVA_HOME=/home/piercingxx/.local/android-toolchain/jdk17
ANDROID_HOME=/home/piercingxx/.local/android-toolchain/sdk` (the jdk17 and SDK
measured at `/home/piercingxx/.local/android-toolchain/`). The task guarantee is
that JAVA_HOME (jdk17) and ANDROID_HOME are injected into every verify at
execution time, so the bare verify lines below rely on that injection — the same
pattern the WS2–WS6 contracts use.

**Dependency on the WS6 data layer (measured this session).** The WS7 receivers
persist received (non-audio) messages through the Room data layer, which WS6
delivered on `main` (`main` carries
`app/src/main/kotlin/com/piercingxx/txxt/data/{ConversationEntity,MessageEntity,ConversationDao,MessageDao,TxxTDatabase,Mappers,BackupJson}.kt`
and the tests `MapperTest.kt`, `BackupJsonTest.kt`). That data layer is **not on
this working branch** (measured: `find app/src -type f` on the current tree
returns no `kotlin/` directory at all). WS7 therefore builds on WS6: the
`service/` compile gate (T3) requires the WS6 `data/` layer to be present on the
branch, so WS6 must be merged in before WS7's compile gate runs. The pure
receive/send *policies* (T1, T2) are DB-independent and JVM-testable regardless,
but the module-wide compile that T1/T2's test tasks depend on needs the data
layer present. This is the same delivery shape WS6's contract described (its
Final gate only passes once the `data/` sources land).

## Deferred verification (the box cannot prove these)

- **A real on-device send and receive.** `done when` requires "on-device checks
  confirm a real send and receive work". The box has no device and no carrier
  channel; it cannot place a real SMS/MMS send or receive. This is the
  operator's on-device check.
- **On-device confirmation that no delivery/read-report request is actually
  made.** The box proves behaviorally (T2) that the send policy never sets the
  delivery/read-report flags; whether the carrier/device honors that on a real
  send is an on-device check.
- **On-device confirmation that MMS is not auto-downloaded.** The box proves
  behaviorally (T2) that the MMS download policy only fetches on explicit tap;
  whether an inbound MMS stays un-downloaded on a real device is an on-device
  check.
- **On-device confirmation that an inbound audio MMS is dropped un-stored.** The
  box proves behaviorally (T1) that the receive policy returns a "drop, do not
  store" decision for audio attachments; whether a real audio MMS is dropped at
  the inbox boundary on-device is an on-device check.
- **On-device confirmation that the auto-reply SMS fires only when enabled.** The
  box proves behaviorally (T1) that the auto-reply decision is gated by the
  off-by-default setting and the per-contact override; whether the SMS actually
  fires on-device exactly when enabled is an on-device check.
- **The manifest permissions taking effect on-device.** The box proves (T3) the
  manifest with the SMS/MMS permissions and receiver registrations compiles and
  is valid; whether the OS grants the runtime permissions and the receivers
  actually fire on real SMS/MMS broadcasts is an on-device check.
- **The auto-reply SMS text matching the spec exactly.** `docs/PRIVACY.md:97`
  gives the reply text *"Voice messages aren't accepted. Send text or a photo."*;
  the box proves the policy carries a reply body, but the exact copy is a
  content decision the operator confirms.

## Tasks

### T1 — Receive policy: audio-MMS drop and the auto-reply decision

Build the pure receive-policy slice of the service layer in `service/` — the
decision logic the `MmsReceiver` and `SmsReceiver` (T3) call, extracted as plain
Kotlin functions with no Android runtime calls so they are JVM-testable in the
app module's unit tests. Two decisions, both from `docs/PRIVACY.md:91-101`:

- **Audio-MMS drop.** Given an inbound MMS attachment part (its MIME/content
  type), decide whether it is audio. If it is, the decision is "drop at the
  inbox boundary, do not download and do not store" (`docs/PRIVACY.md:91-92`).
  The policy returns a drop decision for audio and a pass-through (store) decision
  for non-audio, so the receiver knows an audio MMS must never reach the data
  layer.
- **Auto-reply gate.** Given the auto-reply setting (off by default,
  `docs/PRIVACY.md:96`) and a per-contact override, decide whether to send the
  auto-reply SMS telling the sender voice messages aren't accepted
  (`docs/PRIVACY.md:96-97`). The policy fires the reply only when enabled — the
  default is no reply — and a per-contact override can turn it on for a specific
  sender or keep it off.

The test file `ReceivePolicyTest.kt` in `app/src/test/kotlin/com/piercingxx/txxt/service/`
asserts: an audio attachment yields a drop-un-stored decision; a non-audio
attachment yields a store decision; the auto-reply is a no-op when the setting is
off (the default); the auto-reply fires when enabled; and a per-contact override
turns it on for one sender and off for another. This task owns the receive
policy only; the `SmsReceiver`/`MmsReceiver` Android components that consume it
are T3's, and the send policy is T2, each with its own test file so the verifies
cannot be confused.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.service.ReceivePolicyTest
- files: app/src/main/kotlin/com/piercingxx/txxt/service/ReceivePolicy.kt, app/src/test/kotlin/com/piercingxx/txxt/service/ReceivePolicyTest.kt

### T2 — Send policy: no delivery/read reports and MMS no auto-download

Build the pure send-policy slice of the service layer in `service/` — the
decision logic the send pipeline (T3) calls, extracted as plain Kotlin functions
with no Android runtime calls so they are JVM-testable in the app module's unit
tests. Two decisions, both from `docs/PRIVACY.md:23` and `:151`:

- **No delivery/read reports.** The send-policy options builder never requests a
  delivery or read report. `docs/PRIVACY.md:23` is explicit: "No
  `SMS_DELIVERY_REPORT` / read-report requests." The policy exposes the send
  options such that the delivery/read-report flags are always false/absent, so
  the send pipeline (T3) can never pass a report request to `SmsManager`.
- **MMS no auto-download.** The MMS download policy returns "do not download"
  for an inbound MMS by default; a download is triggered only by an explicit
  tap (`docs/PRIVACY.md:151`). The policy models the two states — pending (not
  downloaded) and fetched-on-tap — and never auto-downloads.

The test file `SendPolicyTest.kt` in `app/src/test/kotlin/com/piercingxx/txxt/service/`
asserts: the send options never carry a delivery-report or read-report flag;
an inbound MMS is not auto-downloaded by default; and a download happens only
when an explicit-tap signal is present. This task owns the send policy only; the
send pipeline Android component that consumes it is T3's, and the receive policy
is T1, each with its own test file so the verifies cannot be confused.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.service.SendPolicyTest
- files: app/src/main/kotlin/com/piercingxx/txxt/service/SendPolicy.kt, app/src/test/kotlin/com/piercingxx/txxt/service/SendPolicyTest.kt

### T3 — SmsReceiver, MmsReceiver, the send pipeline, and the manifest wiring

Build the Android components of the service layer in `service/` and wire them
into the manifest. `SmsReceiver` is a `BroadcastReceiver` for inbound SMS
(`android.provider.Telephony.SMS_RECEIVED`), `MmsReceiver` is a
`BroadcastReceiver` for inbound MMS (`android.provider.Telephony.WAP_PUSH_RECEIVED`),
and the send pipeline wraps `SmsManager` to send SMS/MMS. The receivers consume
the T1 receive policy (audio MMS → drop un-stored; auto-reply gated by the
off-by-default setting and per-contact override) and persist non-audio received
messages through the WS6 `data/` layer. The send pipeline consumes the T2 send
policy (never requests delivery/read reports) and the MMS download policy (no
auto-download; fetch only on explicit tap). The manifest (`app/src/main/AndroidManifest.xml`)
gains the SMS/MMS runtime permissions — the SMS-receive, MMS-receive, and
SMS-send permissions, plus the read/write permissions the receivers need to
persist — and registers both
receivers with their intent filters — completing the default-SMS-handler role the
manifest already declares (`app/src/main/AndroidManifest.xml:4-7`). The verify is
the app-module compile, which proves the `service/` classes compile against the
`core/` model and the WS6 `data/` layer (the first `done when` clause) and that
the manifest with the new permissions and receiver registrations is valid. This
task owns the Android components and the manifest; the pure policies they consume
are T1's and T2's, each with its own verify so the verifies cannot be confused.

- verify: ./gradlew :app:compileDebugKotlin --offline
- files: app/src/main/kotlin/com/piercingxx/txxt/service/SmsReceiver.kt, app/src/main/kotlin/com/piercingxx/txxt/service/MmsReceiver.kt, app/src/main/kotlin/com/piercingxx/txxt/service/SendPipeline.kt, app/src/main/AndroidManifest.xml

## Final gate

The whole-workstream gate is the repo's own test command covering both modules —
it compiles the `app/` module (including the `service/` layer and the manifest
wiring) and runs the full JVM suite (the WS2–WS5 core tests plus the WS6
data-layer tests and the new WS7 receive/send policy tests):

- ./gradlew :app:testDebugUnitTest :core:test --offline

It must exit 0 (83+ core tests plus the WS6 `data/` tests plus the new WS7
`service/` policy tests, 0 failures). No individual task claims this command as
its verify; T1's verify is the `ReceivePolicyTest` node, T2's is the
`SendPolicyTest` node, T3's is the app-module compile, and the Final gate
confirms the whole suite holds together with the new `service/` layer included.
The on-device checks (real send/receive, no report request, no MMS auto-download,
audio MMS dropped un-stored, auto-reply fires only when enabled) remain deferred
(see Deferred verification) because the box has no device or carrier channel.