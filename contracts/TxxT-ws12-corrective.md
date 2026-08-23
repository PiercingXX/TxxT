<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS12-corrective — Wire blocking & starred settings into the live InboundFilter

Scope: the corrective contract that **answers Nagatha's BLOCK finding** on branch
`laundry-bot/queue-TxxT-ws12-i8`. The WS12 delivery's T3 (`SettingsBlocking` +
`SettingsStarred` models) was implemented and unit-tested in isolation, but the
running application never applies the user's blocking/starred edits: `SettingsActivity`
only handles UI and persistence — its blocking button shows a placeholder Toast and
never instantiates `SettingsBlocking`/`SettingsStarred` nor calls `.filter()` — and the
inbound receivers default to an empty `InboundFilter()`. Fix the named wiring defect —
do not rebuild the item.

Nagatha's verdict (abridged): T1, T2, T4, T5 are covered by `SettingsStoreTest`,
`ThemePresetTest`, `SettingsBackupTest`, and `SettingsWiringTest` respectively. T3
(blocking management and starred contacts) has a critical wiring gap: `SettingsBlocking`
and `SettingsStarred` are unit-tested in isolation, but `SettingsWiringTest` does *not*
verify that `SettingsActivity` actually *uses* them to configure the app's runtime
behavior. The diff shows `SettingsActivity` persists the store via `SettingsBackup` but
does *not* instantiate `SettingsBlocking`/`SettingsStarred` and call `.filter()` to update
the running application's blocking logic. The `SettingsBlocking` class exists, but the
code path that *invokes* it to apply the user's changes to the live app is not present.
GROUNDS: `app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsActivity.kt`,
`app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsBlocking.kt`. VERDICT: BLOCK.

## State of the tree (measured this session)

Measured on branch `laundry-bot/queue-TxxT-ws12-i8` (HEAD `f07018f`), materialized into
`.skippy/ws12-i8-tree/` for measurement. The WS12 implementation is present and green:
`ui/SettingsActivity.kt`, `ui/SettingsBlocking.kt`, `ui/SettingsStarred.kt`,
`ui/SettingsStore.kt`, `ui/SettingsBackup.kt`, `block/InboundFilter.kt`, and the five WS12
test suites.

**Gate report (run this session, real numbers).** `./gradlew :app:testDebugUnitTest
--offline --rerun-tasks` (with `JAVA_HOME`/`ANDROID_HOME` injected per the task guarantee)
on the ws12-i8 tree → **BUILD SUCCESSFUL in 12s**, 34 tasks executed. Test report
(`app/build/test-results/testDebugUnitTest/*.xml`, this session): **25 suites, 160 tests,
0 failures, 0 errors**. The WS12 suites are green: `SettingsStoreTest` 6,
`ThemePresetTest` 4, `SettingsBlockingStarredTest` 17, `SettingsBackupTest` 5,
`SettingsWiringTest` 4. The suite passes today *because* it only verifies the models in
isolation and the structural presence of the UI wiring — it never proves the settings
reach the running filter.

**The defect (verified this session, on the ws12-i8 tree).** `SettingsActivity.kt`'s
blocking button (`bindControls`, `findViewById<Button>(R.id.blocking_button)`) shows only
`Toast.makeText(this, "Blocking & starred — coming with WS12 T3", ...)` and never
instantiates `SettingsBlocking`/`SettingsStarred` nor calls `SettingsBlocking.filter()`.
`SettingsWiringTest`'s `SettingsActivity sets FLAG_SECURE and persists the store through
SharedPreferences` only source-greps for `FLAG_SECURE`, `R.layout.activity_settings`,
`SharedPreferences`, and `SettingsBackup` — it never checks that `SettingsActivity` wires
`SettingsBlocking`/`SettingsStarred` into an `InboundFilter`. Meanwhile the inbound
receivers `service/SmsReceiver.kt` and `service/MmsReceiver.kt` default their
`inboundFilter` constructor parameter to `InboundFilter()` (empty settings), so even a
correctly-built filter would not reach the running inbound path. `SettingsBlocking.filter()`
is the seam that builds the `InboundFilter` from the current rules + starred list
(`SettingsBlocking.kt:74-84`), and `SettingsBlockingStarredTest` proves that seam's
behaviour in isolation — but nothing invokes it from the settings screen, and nothing
feeds the result to the receivers.

**Why a seam is needed (verified this session).** `SettingsActivity` is a framework-bound
`Activity` (no Robolectric in the offline cache — `app/build.gradle` test deps are
`junit:junit:4.13.2` and `io.mockk:mockk:1.13.10`), so the activity's wiring cannot be
behaviorally driven in a plain JVM unit test. The codebase's established pattern (WS10
corrective-corrective, WS11 corrective) is to extract a **pure, named seam** the source
calls, and drive that seam's real decision path by name. `SettingsBlocking.filter()` is
already such a seam but is never called from the settings screen and never reaches the
receivers. The fix wires it: a process-wide holder (mirroring the singleton-holder note in
`data/TxxTDatabase.kt:32`) that `SettingsActivity` rebuilds via `SettingsBlocking.filter()`
and that the receivers read.

## Deferred verification (the box cannot prove these)

- **The on-device runtime hop from the settings button to the inbound broadcast.** A plain
  JVM unit test (no Robolectric) cannot deliver a real `Intent` to `SettingsActivity` or
  observe the OS dispatch to the manifest-declared receivers. The box verifies behaviorally
  that `SettingsActivity`'s wiring seam (`LiveInboundFilter.apply`) stores the filter built
  from the settings, and that the receivers' default filter is that live filter (drives
  `SmsReceiver.onReceive` with the live filter set). Whether the OS actually delivers the
  button tap into the field and dispatches the broadcast on-device is a runtime check.
- **The screen *rendering* on-device** and **settings *persisting* across a process restart**
  remain deferred exactly as in the original WS12 contract — this corrective does not touch
  those.

## Tasks

### T1 — Extract `LiveInboundFilter` wiring seam and make `SettingsActivity` apply the blocking/starred settings

The finding is a source defect: `SettingsActivity` never invokes `SettingsBlocking`/`SettingsStarred`
to configure the running app's blocking logic — its blocking button is a placeholder Toast.
Answer it by extracting a pure, named seam that `SettingsActivity` calls as its *only* path
for applying blocking/starred changes, and behaviorally verifying that seam.

Add `app/src/main/kotlin/com/piercingxx/txxt/block/LiveInboundFilter.kt`: an `object` that
holds the process-wide current `InboundFilter` (initialized to `InboundFilter()`), with
`fun apply(blocking: SettingsBlocking, starred: SettingsStarred)` that stores
`blocking.filter(starred)` and a `val current: InboundFilter` getter. Zero `android.*`
imports — JVM-testable, mirroring the WS11-corrective `ThreadMessageLoader` pure-seam
pattern. Change `SettingsActivity.kt`'s blocking button handler to load the persisted
`SettingsBlocking`/`SettingsStarred` from the store and call `LiveInboundFilter.apply(...)`
(removing the placeholder Toast), so the user's blocking/starred edits reach the running
inbound path. This makes the seam the *only* path the settings screen uses to apply blocking,
so the seam's behaviour is the wiring's behaviour.

Create `app/src/test/kotlin/com/piercingxx/txxt/block/LiveInboundFilterTest.kt` — a JVM unit
test that calls `LiveInboundFilter.apply` **by name** and asserts behaviour: (a) after
`apply(SettingsBlocking().blockAddress("+1 555 8888"), SettingsStarred())`, the stored
`current.evaluate("+1 555 8888", "Hello")` returns `MessageDisposition.BLOCK`; (b) after
`apply(blocking, SettingsStarred().star("+1 555 1000"))` with a blocked address on that
contact, `current.evaluate("+1 555 1000", ...)` returns `DELIVER` (starred bypass); and (c) a
wiring assertion reads `SettingsActivity.kt` and confirms the blocking button routes through
`LiveInboundFilter.apply` (so the seam is not dead code). This test would have failed before
the fix because `LiveInboundFilter` did not exist (compile error) and `SettingsActivity`
never applied the settings to any filter.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.block.LiveInboundFilterTest
- files: app/src/main/kotlin/com/piercingxx/txxt/block/LiveInboundFilter.kt, app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsActivity.kt, app/src/test/kotlin/com/piercingxx/txxt/block/LiveInboundFilterTest.kt

### T2 — Make the inbound receivers apply the live filter

The finding's closing defect is that the code path invoking `SettingsBlocking` to apply the
user's changes to the **live app** is not present. Even after T1 builds the filter, the
manifest-declared inbound receivers would still default to an empty `InboundFilter()` and
ignore it. Complete the wiring by making the receivers read the live filter.

Change `service/SmsReceiver.kt` and `service/MmsReceiver.kt` so their `inboundFilter`
constructor parameter **defaults to `LiveInboundFilter.current`** instead of `InboundFilter()`.
Both receivers are manifest-declared no-arg (`AndroidManifest.xml:64-80`), so at runtime they
use this default and apply whatever blocking/starred settings `SettingsActivity` (T1) last
stored. The injectable constructor parameter is unchanged, so the existing receiver tests
(`SmsReceiverBlockingTest`, `MmsReceiverTest`) keep passing with an explicit filter.

Create `app/src/test/kotlin/com/piercingxx/txxt/block/LiveFilterWiringTest.kt` — a JVM unit
test that drives `SmsReceiver.onReceive` **by name** with the live filter applied: set
`LiveInboundFilter.apply(SettingsBlocking().blockAddress("+1 555 8888"), SettingsStarred())`,
then construct an `SmsReceiver` with the **default** filter (no explicit `inboundFilter`),
drive `onReceive` with sender `"+1 555 8888"` and auto-reply enabled, and assert no reply is
sent (the blocked address is dropped by the live filter). A control test with
`LiveInboundFilter.apply(SettingsBlocking(), SettingsStarred())` and a known sender asserts
the reply is sent (deliver). This test would have failed before the fix because the receiver
defaulted to `InboundFilter()` and ignored the live filter entirely.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.block.LiveFilterWiringTest
- files: app/src/main/kotlin/com/piercingxx/txxt/service/SmsReceiver.kt, app/src/main/kotlin/com/piercingxx/txxt/service/MmsReceiver.kt, app/src/test/kotlin/com/piercingxx/txxt/block/LiveFilterWiringTest.kt

## Final gate

The whole-workstream gate is the app module unit test suite — it must pass with the new
corrective tests included:

- ./gradlew :app:testDebugUnitTest --offline

It must exit 0. No individual task claims this command as its verify; T1's verify is the
`LiveInboundFilterTest` node, T2's verify is the `LiveFilterWiringTest` node, and the Final
gate confirms the whole suite (160 existing tests plus the corrective tests) holds together
with the blocking/starred settings wired into the running inbound path. The on-device checks
(the button tap → broadcast hop, the screen *rendering*, settings *persisting* across a
restart) remain deferred (see Deferred verification) because the box has no device.