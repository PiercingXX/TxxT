<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS13 — Accessibility: in-app speech-to-text & text-to-speech (task contract)

Scope: the task contract for **WS13 — Accessibility** of the TxxT workstream
inventory at `contracts/TxxT.md:276`. It adds in-app speech-to-text and in-app
text-to-speech so the app is usable without typing and without reading the screen
(`docs/FEATURES.md:38`), working with any IME including Gboard
(`docs/DESIGN.md:93`). Dictation inserts into the thread's compose field; a
message is read aloud. Both use Android's on-device framework services
(`SpeechRecognizer`, `TextToSpeech`) and must not add any network dependency —
the manifest keeps its deliberate absence of `INTERNET` (`docs/DESIGN.md:91`).

`done when`: on-device checks confirm dictation inserts into the compose field and
text-to-speech reads a message aloud, both without any network dependency.

The box has no device, so the on-device speech-engine hop is deferred (see
Deferred verification). What the box CAN prove behaviorally is the pure seam each
framework call routes through: the dictation-insertion logic and the
read-aloud-text mapping. The contract extracts those as named, JVM-testable seams
(mirroring the established `ThreadMessagePresenter` / `LiveInboundFilter` pure-seam
pattern) and wires the framework calls through them, so the on-device check is the
last hop over already-verified logic.

Authoritative sources read this session: `contracts/TxxT.md:276-283` (the WS13
goal and `done when`), `docs/FEATURES.md:38-40` (the accessibility feature list),
`docs/DESIGN.md:91-94` (the no-`INTERNET` claim and Gboard compatibility),
`app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt` (the compose field
`compose_input` at `:39`, `:62`, `:93-99`), `app/src/main/res/layout/activity_thread.xml`
(the compose bar at `:29-72`), `app/src/main/AndroidManifest.xml` (no `INTERNET`
at `:24-25`, no `RECORD_AUDIO`), `core/src/main/kotlin/com/piercingxx/txxt/core/Message.kt`
(the `Message` model the read-aloud seam maps), and `app/build.gradle` (the test
dependency block at `:75-79` — JUnit 4.13.2 + MockK 1.13.10, no Robolectric in the
offline cache).

## State of the tree (measured this session)

Measured on branch `laundry-bot/queue-TxxT-ws12-corrective-i4` (HEAD `46217cf`),
working tree clean apart from the untracked `contracts/TxxT-ws12-corrective-corrective.md`.
The project is fully built through WS12: `app/` and `core/` modules, the thread
screen with a compose field, and the settings screen. WS13's two named deliverables
are **not started**; the goal is not stale.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| In-app speech-to-text (dictation inserts into the compose field) | **Not started** | no speech source anywhere in `app/src` (searched `SpeechRecognizer`/`dictation`/`mic` this session — zero matches in source); no mic affordance in the compose bar (`activity_thread.xml:29-72` has only `compose_input`, `send_button`, `settings_button`); no `android.permission.RECORD_AUDIO` in `AndroidManifest.xml` |
| In-app text-to-speech (reads a message aloud) | **Not started** | no `TextToSpeech`/`tts`/`read-aloud` source in `app/src` (searched this session — zero matches in source); no read-aloud affordance in `ThreadActivity.kt` or `item_message.xml` |
| No network dependency | **Already satisfied — must be preserved** | `AndroidManifest.xml:24-25` — "Deliberately NO INTERNET permission"; the STT/TTS framework services are local and must not add it |
| Works with any IME including Gboard | **Already satisfied structurally — must be preserved** | `activity_thread.xml:40-52` — `compose_input` is a plain `EditText` (`android:inputType="textShortMessage"`), so any IME works; the in-app dictation must not regress that |

**Gate report (run this session, real numbers).** `./gradlew :app:testDebugUnitTest
--offline` on this branch → **BUILD SUCCESSFUL in 1s**, 34 actionable tasks. Test
report (`app/build/test-results/testDebugUnitTest/*.xml`, this session): **27 test
files, 167 tests, 0 failures, 0 errors**. The suite is green today; it covers
through WS12 (settings, thread, blocking, notifications) and none of it touches
speech, so it passes whether or not WS13 exists — the WS13 seams must each be asked
for by name in their own test nodes.

**Feasibility of the seams (reasoned this session, not yet run).** Both seams are
pure Kotlin over the existing `core` model, so they are JVM-testable exactly like
`ThreadMessagePresenter` (zero `android.*` imports). `DictationInsert.insert` takes
a `String`/`Int`/`String` and returns a `String`; `MessageReadAloud.speakable` takes
a `core.Message` and returns a `String`. Neither needs a device, a mic, a speaker,
or Robolectric — the same plain-JUnit path the 167 existing tests use. The
framework-bound wiring (a `SpeechRecognizer` listener and a `TextToSpeech` call in
`ThreadActivity`) is verified by source-reference wiring tests, the same pattern
the WS12-corrective `LiveInboundFilter` wiring test used.

## Deferred verification (the box cannot prove these)

- **The on-device speech-engine hop.** `SpeechRecognizer` capturing audio and
  returning recognized text, and `TextToSpeech` producing audible speech, require
  a device with a mic and speaker — the box has none. The goal's `done when`
  ("on-device checks confirm dictation inserts into the compose field and
  text-to-speech reads a message aloud") is the operator's on-device check. The
  box verifies behaviorally that the insertion logic and the read-aloud-text
  mapping are correct (T1, T2) and that the framework calls route through them
  (T3, T4); the last hop from the framework service to the field/ear is on-device.
- **Offline recognition actually working on the device.** `SpeechRecognizer` is
  offline-capable but recognizes without a network only when the user has
  downloaded the language pack for the recognition locale — a device state the box
  cannot set or observe. The box verifies the no-`INTERNET` claim (T3's manifest
  assertion); whether recognition works with no network on a given device is the
  operator's check.
- **The mic runtime permission grant.** WS13 adds `RECORD_AUDIO` to the manifest
  (T3, required by `SpeechRecognizer`); whether the OS grants it on-device and the
  recognition service actually starts is a runtime check.
- **The read-aloud affordance *rendering* and the audible result.** Whether the
  message-tap affordance renders per the design and whether `TextToSpeech` sounds
  correct on-device is visual/auditory and the operator's call; the box verifies
  the seam's text mapping and that the wiring names the seam.

## Tasks

### T1 — Extract the dictation-insertion seam (speech-to-text, pure)

Add `app/src/main/kotlin/com/piercingxx/txxt/ui/DictationInsert.kt`: a pure `object`
with `fun insert(current: String, cursor: Int, recognized: String): String` that
inserts the recognized speech into the compose text at the cursor position —
replacing any selected range (the caller passes the selection start as `cursor`
and the selection length is implicit in how the caller trims) and preserving the
existing text around the insertion. Zero `android.*` imports — JVM-testable,
mirroring the `ThreadMessagePresenter` pure-seam pattern. This is the seam the
`SpeechRecognizer` callback in `ThreadActivity` (T3) calls as its **only** path for
writing recognized text into `compose_input`, so the seam's behaviour is the
dictation behaviour. The function must handle the empty-field case, a cursor in
the middle of existing text, and a cursor at the end (append).

Create `app/src/test/kotlin/com/piercingxx/txxt/ui/DictationInsertTest.kt` — a JVM
unit test that calls `DictationInsert.insert` **by name** and asserts behaviour:
(a) inserting into an empty field returns exactly the recognized text; (b) inserting
at a mid-text cursor interleaves the recognized text without dropping the
surrounding characters; (c) appending at the end of existing text yields
`current + recognized`. This test would have failed before the fix because
`DictationInsert` did not exist (compile error). This task owns only the two files
named in its `- files:` bullet.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.ui.DictationInsertTest
- files: app/src/main/kotlin/com/piercingxx/txxt/ui/DictationInsert.kt, app/src/test/kotlin/com/piercingxx/txxt/ui/DictationInsertTest.kt

### T2 — Extract the read-aloud-text seam (text-to-speech, pure)

Add `app/src/main/kotlin/com/piercingxx/txxt/ui/MessageReadAloud.kt`: a pure `object`
with `fun speakable(message: Message): String` that maps a `core.Message` to the
text read aloud when the user taps it — the message body, prefixed with a short
sender label for incoming messages so the listener knows who spoke (e.g.
`"from <sender>: <body>"`), and the body alone for outgoing messages. Zero
`android.*` imports — JVM-testable. This is the seam the `TextToSpeech` call in
`ThreadActivity` (T4) uses as its **only** source of the text to speak, so the
seam's mapping is the read-aloud content. The mapping must not read empty or
whitespace-only bodies aloud (return an empty string for those, and the caller
skips the `speak`).

Create `app/src/test/kotlin/com/piercingxx/txxt/ui/MessageReadAloudTest.kt` — a JVM
unit test that calls `MessageReadAloud.speakable` **by name** with constructed
`core.Message` values and asserts behaviour: (a) an incoming message yields a
string containing the sender address and the body; (b) an outgoing message yields
the body without a sender prefix; (c) a blank body yields an empty string. This
test would have failed before the fix because `MessageReadAloud` did not exist
(compile error). This task owns only the two files named in its `- files:` bullet.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.ui.MessageReadAloudTest
- files: app/src/main/kotlin/com/piercingxx/txxt/ui/MessageReadAloud.kt, app/src/test/kotlin/com/piercingxx/txxt/ui/MessageReadAloudTest.kt

### T3 — Wire dictation into the thread screen and declare the mic permission

Wire the T1 seam into the running thread screen so dictation inserts into the
compose field. Add a minimal mic affordance to the compose bar
(`app/src/main/res/layout/activity_thread.xml`, inside the `compose_bar`
`LinearLayout` at `:29-72`) — a small button beside `send_button` that triggers
in-app dictation; it must not displace or break the plain `EditText` `compose_input`
(the Gboard/any-IME compatibility is structural and must hold). In
`app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt`, create a
`SpeechRecognizer` (via `SpeechRecognizer.createSpeechRecognizer(this)`), register
a `RecognitionListener`, and in its `onResults` handler write the recognized text
into `composeInput` **only through** `DictationInsert.insert` — compute the result
as `DictationInsert.insert(current, cursor, bestHypothesis)` and
`composeInput.setText(result)`. Add `android.permission.RECORD_AUDIO` to
`app/src/main/AndroidManifest.xml` (required by `SpeechRecognizer`), keeping the
deliberate absence of `INTERNET` intact.

Create `app/src/test/kotlin/com/piercingxx/txxt/ui/DictationWiringTest.kt` — a JVM
unit test that proves the wiring and the privacy claim, and FAILS if either
regresses: (a) reads `app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt`
source and asserts it references `DictationInsert` and `SpeechRecognizer` by name,
so the seam is wired into the running thread (not dead code) — the behavioural
correctness of the seam itself is T1's verify; (b) reads the manifest source and
asserts `android.permission.RECORD_AUDIO` is declared and that
`android.permission.INTERNET` is **absent**, so the STT feature adds no network
dependency. This task owns the four files named in its `- files:` bullet; the
`item_message.xml`/`ThreadAdapter` read-aloud affordance is T4's scope.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.ui.DictationWiringTest
- files: app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt, app/src/main/res/layout/activity_thread.xml, app/src/main/AndroidManifest.xml, app/src/test/kotlin/com/piercingxx/txxt/ui/DictationWiringTest.kt

### T4 — Wire text-to-speech into the thread screen

Wire the T2 seam into the running thread screen so a message is read aloud. In
`app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt`, create a
`TextToSpeech` engine and add a read-aloud affordance on a message row: a tap on a
message (via the `ThreadAdapter` row binding, or a long-press handler in the
activity if the adapter is not easily extended) reads that message aloud by calling
`TextToSpeech.speak(MessageReadAloud.speakable(message), ...)` — the seam is the
**only** source of the spoken text, and the caller skips `speak` when the seam
returns an empty string (so blank bodies are never read). The affordance must not
introduce a voice-message send/receive path (PRIVACY.md §5 — voice is never sent;
this is read-only TTS of existing text).

Create `app/src/test/kotlin/com/piercingxx/txxt/ui/TtsWiringTest.kt` — a JVM unit
test that proves the wiring and FAILS if it regresses: reads
`app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt` source and asserts
it references `MessageReadAloud` and `TextToSpeech` by name, so the read-aloud
seam is wired into the running thread (not dead code) — the behavioural correctness
of the seam itself is T2's verify. This task owns the two files named in its
`- files:` bullet; the dictation wiring and the manifest are T3's scope.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.ui.TtsWiringTest
- files: app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt, app/src/test/kotlin/com/piercingxx/txxt/ui/TtsWiringTest.kt

## Final gate

The whole-workstream gate is the repo's own app-module test command, which runs the
full JVM suite — the two new pure-seam tests created by T1 and T2, the two wiring
tests created by T3 and T4, and the untouched 167 existing tests:

- ./gradlew :app:testDebugUnitTest --offline

It must exit 0 (all app-module JVM tests, 0 failures). No individual task claims
this command as its verify; T1's verify is the `DictationInsertTest` node, T2's is
the `MessageReadAloudTest` node, T3's is the `DictationWiringTest` node, T4's is
the `TtsWiringTest` node, and the Final gate confirms the whole suite holds
together with the speech seams wired in. The on-device checks (dictation actually
inserting into the compose field, TTS actually reading a message aloud, both with
no network) remain deferred (see Deferred verification) because the box has no
device, mic, or speaker.