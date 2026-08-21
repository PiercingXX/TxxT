<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS7A — Wire the SMS/MMS receivers into the app's execution flow (task contract)

Scope: the task contract for **WS7A — Wire the SMS/MMS receivers into the app's
execution flow** of the TxxT project. The audit found WS7's `SmsReceiver`,
`MmsReceiver` and the send pipeline to be **dead code**: declared in
`AndroidManifest.xml` but never reached from the running application, so no
inbound SMS or MMS is ever handled. WS7A wires them in — registration/dispatch
reaches `SmsReceiver.onReceive` and `MmsReceiver.onReceive` for a real inbound
intent, those handlers route through the existing `ReceivePolicy` and send
pipeline, and the privacy behaviour WS7 already implements (no delivery/read
reports, MMS auto-download off, inbound audio MMS dropped un-stored, auto-reply
only when enabled) holds on that live path. **Do NOT re-extract or rename
anything**: WS7's policy classes (`ReceivePolicy`, `SendPolicy`) and their tests
are correct and already delivered — the gap is only that nothing calls them.

`done when`: a JVM unit test drives an inbound SMS intent and an inbound
audio-MMS intent through the app's registered receiver entry points and asserts
the resulting behaviour (auto-reply decision honoured, audio MMS dropped
un-stored), such that the test FAILS if the receivers are never invoked; and a
test asserts the manifest-declared receiver class names resolve to those classes.
`./gradlew :app:testDebugUnitTest --offline` passes. Compilation is NOT evidence
here — WS7 compiled green while the receivers were unreachable.

Authoritative sources read this session: `contracts/TxxT-ws7.md` (the WS7
contract and its `done when`), `app/src/main/AndroidManifest.xml` (the receiver
registrations at `:48-67`), `app/src/main/kotlin/com/piercingxx/txxt/service/`
(`SmsReceiver.kt`, `MmsReceiver.kt`, `SendPipeline.kt`, `ReceivePolicy.kt`,
`SendPolicy.kt`), the existing vacuous tests
`app/src/test/kotlin/com/piercingxx/txxt/service/{SmsReceiverTest,MmsReceiverTest,SendPipelineTest}.kt`,
`app/build.gradle` (the test dependency block at `:72-75`), and the offline
Gradle dependency cache measured on disk (MockK `1.13.10` present).

## State of the tree (measured this session)

Measured on branch `laundry-bot/queue-TxxT-ws7-i6` (HEAD `3959339`), working tree
clean apart from `contracts/TxxT.md` (pre-existing modification). WS7's service
layer is fully present and compiles; the gap is that the receiver entry points
are never exercised. Every deliverable the WS7A goal names is **already present
as source** but the **tests that invoke them are vacuous** — they re-test the
policies and never call `onReceive`/`sendSms`, so they pass whether or not the
receivers are wired.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| `SmsReceiver` declared in the manifest | **Present** | `app/src/main/AndroidManifest.xml:48-55` — `<receiver android:name=".service.SmsReceiver" ...>` with the `SMS_RECEIVED` intent-filter and `android:permission="android.permission.BROADCAST_SMS"` |
| `MmsReceiver` declared in the manifest | **Present** | `app/src/main/AndroidManifest.xml:59-67` — `<receiver android:name=".service.MmsReceiver" ...>` with the `WAP_PUSH_RECEIVED` intent-filter, `mimeType="application/vnd.wap.mms-message"`, and `android:permission="android.permission.BROADCAST_WAP_PUSH"` |
| `SmsReceiver.onReceive` routes through `ReceivePolicy` and the send pipeline | **Present (source)** | `app/src/main/kotlin/com/piercingxx/txxt/service/SmsReceiver.kt:21-35` — calls `ReceivePolicy.shouldAutoReply` and, when it fires, `SendPipeline.sendSms` with `ReceivePolicy.AUTO_REPLY_BODY` |
| `MmsReceiver.onReceive` routes through `ReceivePolicy` and drops audio un-stored | **Present (source)** | `app/src/main/kotlin/com/piercingxx/txxt/service/MmsReceiver.kt:22-36` — calls `ReceivePolicy.decideAttachment(intent.type)` and `abortBroadcast()` on `DROP_UNSTORED` |
| Send pipeline never requests delivery/read reports | **Present (source)** | `app/src/main/kotlin/com/piercingxx/txxt/service/SendPipeline.kt:24-43` — passes `null` sent/delivery `PendingIntent`s to `SmsManager` |
| A test that DRIVES the receiver entry points and fails if they are never invoked | **Not started (vacuous)** | `app/src/test/kotlin/com/piercingxx/txxt/service/SmsReceiverTest.kt` and `MmsReceiverTest.kt` never call `onReceive` — they re-test `ReceivePolicy.shouldAutoReply`/`decideAttachment` directly (e.g. `SmsReceiverTest.kt:27`, `MmsReceiverTest.kt:29`) |
| A test that asserts the manifest-declared receiver class names resolve to those classes | **Not started** | no such test exists in the tree (searched `app/src/test` this session) |

**Gate report (run this session).** `./gradlew :app:testDebugUnitTest --offline`
(with `JAVA_HOME=/home/piercingxx/.local/android-toolchain/jdk17` and
`ANDROID_HOME=/home/piercingxx/.local/android-toolchain/sdk` injected, per the
task guarantee) → **BUILD SUCCESSFUL in 12s**. Test report
(`app/build/test-results/testDebugUnitTest/*.xml`, this session):
`ReceivePolicyTest` 7, `SendPolicyTest` 3, `SmsReceiverTest` 3, `MmsReceiverTest`
3, `SendPipelineTest` 2 — **18 tests, 0 failures, 0 errors** across 5 test files.
The three receiver/pipeline test files are the vacuous ones: they pass while the
receivers are unreachable, which is exactly the finding WS7A must close.

**Feasibility measured this session (prototype, then reverted).** To confirm the
contract's verifies can actually drive the receivers in a plain JVM unit test
(no Robolectric in the offline cache — measured: `find .../io.mockk` and the
androidx `test`/`robolectric` dirs are absent), I prototyped a MockK-based test
and ran it, then deleted the prototype and reverted `app/build.gradle`. Findings:
- **MockK `1.13.10` is in the offline cache** (measured:
  `/home/piercingxx/.gradle/caches/modules-2/files-2.1/io.mockk/mockk/1.13.10/`),
  so adding `testImplementation 'io.mockk:mockk:1.13.10'` resolves under `--offline`.
- **`SmsReceiver.onReceive` is drivable**: `mockkStatic(Telephony.Sms.Intents::class)`
  + a mocked `SmsMessage` (`originatingAddress`, `messageBody`) + `mockkObject(SendPipeline)`
  + a mocked `Intent` lets a test call `SmsReceiver().onReceive(context, intent)`
  and assert the auto-reply decision. **Prototype test passed** this session.
- **`MmsReceiver.onReceive` is drivable**: `spyk(MmsReceiver())` + a mocked
  `Intent` (`action`, `type`) lets a test call `onReceive(context, intent)` and
  `verify { receiver.abortBroadcast() }` for an audio MIME type. **Prototype test
  passed** this session.
- **`SendPipeline.sendSms`'s exact-arg null-report verify was inconclusive**:
  `mockkStatic(SmsManager::class)` + a mocked `SmsManager` compiled and ran, but
  the exact-argument `verify { sendTextMessage("+1555…", null, "hi", null, null) }`
  did not match the recorded call in my prototype (the mockable-jar `SmsManager`
  final stubbing is finicky). This is a **hypothesis for the implementer to
  instrument-and-observe**, not a proven failure of the approach — the two
  receiver tests (the goal's core `done when`) are proven feasible.

## Deferred verification (the box cannot prove these)

- **A real on-device inbound SMS/MMS broadcast reaching the receivers.** The box
  proves behaviorally (T1, T2) that `onReceive` is invoked and routes through the
  policy; whether the OS dispatches a real `SMS_RECEIVED`/`WAP_PUSH_RECEIVED`
  broadcast to the manifest-declared receivers on a device is an on-device check
  (requires the app to be the default SMS handler, which is a runtime state).
- **The `SendPipeline` null-report exact-argument verify.** My prototype's
  exact-arg `verify` on the mocked `SmsManager.sendTextMessage` did not match this
  session (see feasibility note). The approach (mock `SmsManager.getDefault()` and
  assert the null report `PendingIntent`s) is sound but needs implementation-time
  iteration to match the mockable-jar stubbing. Until a real run passes, the
  exact-arg claim in T4 stays **unverified**; T4's verify is that the test file's
  behaviour passes, and the implementer must make the stubbing match.
- **The manifest-resolution test (T3).** I prototyped the approach (read
  `src/main/AndroidManifest.xml`, assert the `.service.SmsReceiver`/`.service.MmsReceiver`
  names appear, and `Class.forName("com.piercingxx.txxt.service.SmsReceiver")`
  resolves to the class) but did not observe it pass this session (the prototype
  run failed earlier in the same file at the SendPipeline test). High confidence,
  but **unverified** until a real run.

## Tasks

### T1 — Drive `SmsReceiver.onReceive` through the auto-reply decision

Rewrite `app/src/test/kotlin/com/piercingxx/txxt/service/SmsReceiverTest.kt` so it
genuinely invokes the registered receiver's entry point instead of re-testing
`ReceivePolicy` directly. Add `testImplementation 'io.mockk:mockk:1.13.10'` to
`app/build.gradle` (MockK `1.13.10` is in the offline cache, measured this
session — it is the enabler that lets a plain JVM unit test drive the Android
`BroadcastReceiver` without Robolectric, which is not in the cache). The test
constructs a real `SmsReceiver`, `mockkStatic(Telephony.Sms.Intents::class)` so
`getMessagesFromIntent` returns a mocked `SmsMessage` (with `originatingAddress`
and `messageBody`), mocks the `Intent` with the `SMS_RECEIVED_ACTION` action, and
calls `SmsReceiver().onReceive(context, intent)`. It asserts the auto-reply
decision is honoured on the live path: with the off-by-default posture the
receiver never sends a reply. The assert must be on the receiver's actual call —
`mockkObject(SendPipeline)` and `verify(exactly = 0) { SendPipeline.sendSms(...) }`
— so the test FAILS (compile or verify) if `SmsReceiver`'s `onReceive` is never
invoked or does not route through `SendPipeline`. This task owns the
`SmsReceiverTest` file and the `build.gradle` MockK dependency; the other test
files are owned by their own tasks so the verifies cannot be confused.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.service.SmsReceiverTest
- files: app/build.gradle, app/src/test/kotlin/com/piercingxx/txxt/service/SmsReceiverTest.kt

### T2 — Drive `MmsReceiver.onReceive` through the audio-MMS drop

Rewrite `app/src/test/kotlin/com/piercingxx/txxt/service/MmsReceiverTest.kt` so it
genuinely invokes the registered receiver's entry point instead of re-testing
`ReceivePolicy.decideAttachment` directly. Using MockK (added in T1), the test
builds `spyk(MmsReceiver())`, mocks the `Intent` with the `WAP_PUSH_RECEIVED`
action and an audio MIME type (e.g. `audio/mpeg`), calls
`receiver.onReceive(context, intent)`, and `verify(exactly = 1) { receiver.abortBroadcast() }`
— proving the inbound audio MMS is dropped un-stored at the inbox boundary. It
also asserts a non-audio MIME type (e.g. `image/jpeg`) does **not** call
`abortBroadcast()`. The assert is on the receiver's actual `abortBroadcast()` call,
so the test FAILS if `MmsReceiver`'s `onReceive` is never invoked or does not
route through `ReceivePolicy`. This task owns the `MmsReceiverTest` file only; the
`SmsReceiverTest`/`SendPipelineTest` files and the `build.gradle` dependency are
owned by their own tasks.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.service.MmsReceiverTest
- files: app/src/test/kotlin/com/piercingxx/txxt/service/MmsReceiverTest.kt

### T3 — Assert the manifest-declared receiver class names resolve to those classes

Add `app/src/test/kotlin/com/piercingxx/txxt/service/ReceiverManifestTest.kt`: a
JVM unit test that reads the manifest source (`src/main/AndroidManifest.xml`,
relative to the app module's unit-test working directory) and asserts both
`android:name=".service.SmsReceiver"` and `android:name=".service.MmsReceiver"`
appear, then asserts the declared names resolve to real classes on the classpath
via `Class.forName("com.piercingxx.txxt.service.SmsReceiver")` and
`Class.forName("com.piercingxx.txxt.service.MmsReceiver")` equalling the
`SmsReceiver`/`MmsReceiver` classes. This is the goal's second `done when` clause
— it proves the manifest registration and the code are the same receiver, so a
registration that names a class that does not exist (or a class that was never
wired) fails the test. This task owns only the new test file named in its
`- files:` bullet.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.service.ReceiverManifestTest
- files: app/src/test/kotlin/com/piercingxx/txxt/service/ReceiverManifestTest.kt

### T4 — Drive `SendPipeline` and assert the null-report send request

Rewrite `app/src/test/kotlin/com/piercingxx/txxt/service/SendPipelineTest.kt` so it
invokes the real `SendPipeline.sendSms`/`sendMms` (via MockK, added in T1) instead
of re-testing `SendPolicy.requestsDeliveryReport()`/`requestsReadReport()` directly.
The test mocks `SmsManager.getDefault()` to return a mocked `SmsManager`, calls
`SendPipeline.sendSms(context, destination, body)`, and asserts `sendTextMessage`
is invoked with `null` for the sent and delivery `PendingIntent`s — proving the
send path never requests a delivery or read report (`docs/PRIVACY.md:23`).
**Feasibility caveat (measured this session):** my prototype's exact-argument
`verify` on the mocked `SmsManager.sendTextMessage` did not match the recorded
call (the mockable-jar `SmsManager` final stubbing is finicky). The intended
approach is sound; the implementer must instrument-and-observe the recorded call
and match the stubbing (e.g. relax the matchers and capture the args) so the
assert proves the null report intents. This task owns the `SendPipelineTest` file
only.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.service.SendPipelineTest
- files: app/src/test/kotlin/com/piercingxx/txxt/service/SendPipelineTest.kt

## Final gate

The whole-workstream gate is the repo's own app-module test command, which runs
the full JVM suite — the rewritten `SmsReceiverTest`/`MmsReceiverTest`/
`SendPipelineTest` that drive the receivers and send pipeline, the new manifest
receiver-resolution test created by T3, and the untouched
`ReceivePolicyTest`/`SendPolicyTest`:

- ./gradlew :app:testDebugUnitTest --offline

It must exit 0 (all app-module JVM tests, 0 failures). No individual task claims
this command as its verify; T1's verify is the `SmsReceiverTest` node, T2's is the
`MmsReceiverTest` node, T3's is the manifest receiver-resolution test node, T4's is the
`SendPipelineTest` node, and the Final gate confirms the whole suite holds
together with the receivers actually driven. The on-device checks (a real
SMS/MMS broadcast dispatching to the receivers, the default-SMS-handler role
granted) remain deferred (see Deferred verification) because the box has no
device or carrier channel.