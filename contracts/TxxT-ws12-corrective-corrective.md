<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS12-corrective-corrective — Behaviorally wire persisted blocking/starred settings into the running InboundFilter

Scope: the corrective-corrective contract that **answers Nagatha's BLOCK of the first
corrective** (`TxxT-ws12-corrective`, delivered on `laundry-bot/queue-TxxT-ws12-corrective-i4`).
The first corrective extracted a `LiveInboundFilter` seam and wired the receivers to it, but
its T1 verify proved the wiring only by **source-grep** (a structural check Nagatha's audit
explicitly blocks — "a structural check proves presence, never behaviour"), and the delivered
`SettingsActivity` blocking button applies **empty** `SettingsBlocking()`/`SettingsStarred()`
models — never the user's persisted blocking/starred edits. So the exact defect the original
WS12 finding described (the settings never reach the running inbound filter) is **still present**.
Fix the named wiring defect behaviorally — do not rebuild the item.

Nagatha's audit packet (abridged): 4 target findings, 2 self. The target cluster is the T1
wiring gap: `LiveInboundFilterTest` proves the seam in isolation with explicitly-passed models
and a source-grep assertion (`SettingsActivity.kt` "contains `LiveInboundFilter.apply`"), but
**no test behaviorally proves the user's persisted blocking/starred settings reach the running
filter**. The delivered button handler instantiates empty models because there is no persistence
for blocking/starred settings to load. GROUNDS:
`app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsActivity.kt`,
`app/src/main/kotlin/com/piercingxx/txxt/block/LiveInboundFilter.kt`. VERDICT: BLOCK.

## State of the tree (measured this session)

Measured on branch `laundry-bot/queue-TxxT-ws12-i8` (HEAD `1bc3cb5`), working tree clean. The
WS12 implementation is present and green; the first corrective's seam and receiver wiring were
delivered on `queue-TxxT-ws12-corrective-i4` but the corrective was BLOCKED. The deliverables
this corrective-corrective must fix are **present as source but the wiring is behaviorally
broken** — the button applies empty models.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| `SettingsActivity` blocking button applies the user's blocking/starred settings to the running filter | **Broken** | `ui/SettingsActivity.kt` (delivered on `queue-TxxT-ws12-corrective-i4`) — the `blocking_button` handler calls `LiveInboundFilter.apply(SettingsBlocking(), SettingsStarred())` with **empty** models; the comment admits "the button applies the current (empty) models". It never loads persisted blocking/starred settings |
| A behaviorally-verified seam that loads persisted blocking/starred and applies them | **Not started** | `LiveInboundFilter.apply(blocking, starred)` exists (delivered) but is only verified in isolation with explicitly-passed models; no test drives a *persisted* blocking/starred set through it |
| Persistence for the blocking/starred settings | **Not started** | `ui/SettingsStore.kt` is a five-field data class (`lockScreenPrivacy`, `notificationPosture`, `autoSyncTheme`, `themePreset`, `fontMode`) with **no** blocking/starred fields; `ui/SettingsBackup.kt` has **no** blocking/starred references (grep this session: zero matches) |
| Receivers apply the live filter | **Present (from the corrective)** | `service/SmsReceiver.kt`/`MmsReceiver.kt` default `inboundFilter` to `LiveInboundFilter.current`; `LiveFilterWiringTest` drives `SmsReceiver.onReceive` by name. This part of the corrective was correct |

**Gate report (run this session, real numbers).** `./gradlew :app:testDebugUnitTest --offline
--rerun-tasks` (with `JAVA_HOME`/`ANDROID_HOME` injected per the task guarantee) on the ws12-i8
tree → **BUILD SUCCESSFUL**, **25 suites, 160 tests, 0 failures, 0 errors**. The suite passes
today *because* it verifies the models in isolation and the structural presence of the wiring —
it never proves the persisted blocking/starred settings reach the running filter (the same
blind spot Nagatha's original finding and this corrective-corrective's finding both name).

**The defect (verified this session, on the delivered corrective).** The corrective's T1 was
supposed to "load the persisted `SettingsBlocking`/`SettingsStarred` from the store" and call
`LiveInboundFilter.apply(...)`. The delivered code instead calls
`LiveInboundFilter.apply(SettingsBlocking(), SettingsStarred())` — empty models. There is no
persistence for blocking/starred settings (`SettingsStore` and `SettingsBackup` carry none,
measured this session), so the button has nothing to load and the user's blocking/starred edits
can never reach the running filter. `LiveInboundFilterTest`'s wiring assertion is a
**source-grep** (`sourceText("ui/SettingsActivity.kt").contains("LiveInboundFilter.apply")`) — a
structural check that proves the call site exists, not that the persisted settings are applied.
A behavior test that persists a blocked address and drives the load-and-apply path by name would
have failed before this corrective-corrective, because no such path existed.

## Deferred verification (the box cannot prove these)

- **The on-device runtime hop from the settings button to the inbound broadcast.** A plain JVM
  unit test (no Robolectric in the offline cache) cannot deliver a real `Intent` to
  `SettingsActivity` or observe the OS dispatch to the manifest-declared receivers. The box
  verifies behaviorally that a pure seam (the load-and-apply path `SettingsActivity` calls)
  stores a filter built from the *persisted* blocking/starred settings, and that the receivers'
  default filter is that live filter. Whether the OS delivers the button tap into the field and
  dispatches the broadcast on-device is a runtime check.
- **The blocking/starred *editing* UI.** `SettingsBlocking`/`SettingsStarred` are the
  persistence-shaped models the settings screen edits; the screen's editing controls for them
  are WS12 T3's scope and are not built. This corrective-corrective establishes the persistence
  and the load-and-apply path so that whatever editing UI later lands drives the same seam; the
  editing controls themselves stay deferred.
- **The screen *rendering* on-device** and **settings *persisting* across a process restart**
  remain deferred exactly as in the original WS12 contract — this corrective-corrective does not
  touch those.

## Tasks

### T1 — Persist blocking/starred settings and add a load-and-apply seam

The finding is a source defect: the delivered blocking button applies **empty**
`SettingsBlocking()`/`SettingsStarred()` because there is no persistence for those settings to
load, and the only wiring proof is a source-grep. Answer it by (a) giving the blocking/starred
settings a real persistence home and (b) extracting a pure, named seam that loads the persisted
settings and applies them to the running filter — behaviorally verified by name.

Add `app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsBlockingStore.kt`: a pure-Kotlin
(zero `android.*`) persistence holder for the blocking/starred settings, mirroring the
`SettingsStore`/`SettingsBackup` style. It holds the current `SettingsBlocking` and
`SettingsStarred`, with `fun save(blocking: SettingsBlocking, starred: SettingsStarred)` and
`fun load(): Pair<SettingsBlocking, SettingsStarred>` that round-trips through a
JSON-serializable form (Gson `2.10.1` is already a dependency) so the settings the user edits
persist and can be read back. Defaults to empty `SettingsBlocking()`/`SettingsStarred()` when
nothing has been saved.

Extend `app/src/main/kotlin/com/piercingxx/txxt/block/LiveInboundFilter.kt` with a pure seam
`fun applyPersisted()` that reads `SettingsBlockingStore.load()` and calls `apply(blocking,
starred)` — the *only* path the settings screen uses to apply blocking/starred changes from
persistence. This makes the seam's behaviour the wiring's behaviour: a test that persists a
blocked address and calls `applyPersisted()` by name must observe the live filter block it.

Create `app/src/test/kotlin/com/piercingxx/txxt/ui/SettingsBlockingStoreTest.kt` — a JVM unit
test that calls `SettingsBlockingStore.save`/`load` **by name** and asserts the round-trip: (a)
after `save(SettingsBlocking().blockAddress("+1 555 8888"), SettingsStarred())`, `load()`
returns a blocking model whose `filter(starred).evaluate("+1 555 8888", "Hello")` returns
`MessageDisposition.BLOCK`; (b) `load()` with nothing saved returns empty models; (c) a starred
contact round-trips so `evaluate` returns `DELIVER` (starred bypass). This test would have failed
before the fix because `SettingsBlockingStore` did not exist (compile error).

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.ui.SettingsBlockingStoreTest
- files: app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsBlockingStore.kt, app/src/main/kotlin/com/piercingxx/txxt/block/LiveInboundFilter.kt, app/src/test/kotlin/com/piercingxx/txxt/ui/SettingsBlockingStoreTest.kt

### T2 — Make the blocking button load-and-apply the persisted settings, behaviorally verified

The finding's closing defect is that the button applies empty models, so the user's persisted
blocking/starred edits never reach the running app. Complete the wiring by making the button
drive the load-and-apply seam, and prove behaviorally (not by source-grep) that a *persisted*
blocking/starred set reaches the running filter.

Change `app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsActivity.kt`'s `blocking_button`
handler to call `LiveInboundFilter.applyPersisted()` (the seam from T1) instead of
`LiveInboundFilter.apply(SettingsBlocking(), SettingsStarred())` with empty models — so the
button loads whatever blocking/starred settings are persisted and applies them to the running
inbound path. Remove the "applies the current (empty) models" comment.

Create `app/src/test/kotlin/com/piercingxx/txxt/block/LiveBlockingApplyTest.kt` — a JVM unit
test that drives the load-and-apply path **by name** with a persisted set: call
`SettingsBlockingStore.save(SettingsBlocking().blockAddress("+1 555 8888"), SettingsStarred())`,
then `LiveInboundFilter.applyPersisted()`, then assert
`LiveInboundFilter.current.evaluate("+1 555 8888", "Hello")` returns `MessageDisposition.BLOCK`.
A control test saves `SettingsBlocking()`/`SettingsStarred().star("+1 555 2000")`, applies, and
asserts `evaluate("+1 555 2000", ...)` returns `DELIVER` (starred bypass through the persisted
seam). This test would have failed before the fix because `applyPersisted` did not exist and the
button applied empty models. It is the behavioral proof the corrective's source-grep assertion
never provided.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.block.LiveBlockingApplyTest
- files: app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsActivity.kt, app/src/test/kotlin/com/piercingxx/txxt/block/LiveBlockingApplyTest.kt

## Final gate

The whole-workstream gate is the app module unit test suite — it must pass with the new
corrective-corrective tests included:

- ./gradlew :app:testDebugUnitTest --offline

It must exit 0. No individual task claims this command as its verify; T1's verify is the
`SettingsBlockingStoreTest` node, T2's verify is the `LiveBlockingApplyTest` node, and the Final
gate confirms the whole suite (the 160 existing tests plus the corrective-corrective tests) holds
together with the persisted blocking/starred settings wired into the running inbound path. The
on-device checks (the button tap → broadcast hop, the blocking/starred editing UI, the screen
*rendering*, settings *persisting* across a restart) remain deferred (see Deferred verification)
because the box has no device.