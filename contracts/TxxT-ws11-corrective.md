<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS11-corrective — Behaviorally verify the thread's conversationId → query data flow

Scope: the corrective contract that **answers Nagatha's BLOCK finding** on branch
`laundry-bot/queue-TxxT-ws11-i6`. The WS11 delivery's T3 (`ThreadActivity` +
`ThreadAdapter` wiring, manifest registration, and **launch**) was implemented
cleanly, but its verification test `ThreadWiringTest` verifies only the *presence*
of the wiring by source-grep — it never behaviorally verifies that the
`conversationId` retrieved from the launch intent is actually passed to the
database query and drives the data load. Fix the named coverage gap — do not
rebuild the item.

Nagatha's verdict (abridged): the test `ThreadActivity drives the ThreadAdapter
and routes sends through SendPipeline` only checks for the presence of the strings
`SendPipeline.sendSms` and `ThreadAdapter()` in the source text; it does not verify
that the `conversationId` retrieved from the intent is actually passed to the
`observeMessages` flow or used to query the database. The behavioral test `the
adapter's onBindViewHolder reaches ThreadMessagePresenter` is isolated to the
adapter and presenter, not the full activity launch flow. The test suite therefore
fails to verify the *wiring* of the `conversationId` from the intent to the
database query, which is a core part of the "launch" and "wiring" contract for T3.
GROUNDS: `app/src/test/kotlin/com/piercingxx/txxt/ui/ThreadWiringTest.kt`,
`app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt`. VERDICT: BLOCK.

## State of the tree (measured this session)

Measured on branch `laundry-bot/queue-TxxT-ws11-i6` (HEAD `c3b979f`), checked out
into `.skippy/tmp-ws11-corrective/` for measurement. The WS11 implementation is
present: `ui/ThreadActivity.kt`, `ui/ThreadAdapter.kt`,
`ui/ThreadMessagePresenter.kt`, `res/layout/activity_thread.xml`,
`res/layout/item_message.xml`, and the three WS11 test suites.

**Gate report (run this session, real numbers).** `./gradlew :app:testDebugUnitTest
--offline --rerun-tasks` (with `JAVA_HOME`/`ANDROID_HOME` injected per the task
guarantee) → **BUILD SUCCESSFUL in 11s**, 34 tasks executed. Test report
(`app/build/test-results/testDebugUnitTest/*.xml`, this session): **19 suites, 121
tests, 0 failures, 0 errors**. The WS11 suites are green: `ThreadLayoutTest` 4,
`ThreadMessagePresenterTest` 4, `ThreadWiringTest` 6.

**The defect (verified this session).** `ThreadWiringTest.kt`'s `ThreadActivity
drives the ThreadAdapter and routes sends through SendPipeline` reads the
`ThreadActivity.kt` source text and asserts it `contains("ThreadAdapter()")`,
`contains("adapter.submit")`, `contains("SendPipeline.sendSms")`, and
`contains("FLAG_SECURE")` — a structural grep that proves the code *exists*, not
that the intent's `conversationId` reaches the query. The behavioral test `the
adapter's onBindViewHolder reaches ThreadMessagePresenter` builds a `ThreadAdapter`
with a fixed mock message (`conversationId = 1L`) and never involves `ThreadActivity`
or the intent. So the scenario the finding names — "launching with a specific
conversation ID and having that ID drive the data load" — is untested.

**Why the source needs a seam (verified this session).** `ThreadActivity.kt`
`onCreate` reads `conversationId = intent.getLongExtra(EXTRA_CONVERSATION_ID, 0L)`
and `observeMessages()` inlines the load as
`database.messageDao().observeForConversation(conversationId).collect { ... }` in a
`private` method on `Dispatchers.Main`, with `database` a `private` lazy Room
instance. This is not behaviorally testable in a plain JVM unit test (no Robolectric
in the offline cache — `app/build.gradle`'s test deps are `junit:junit:4.13.2` and
`io.mockk:mockk:1.13.10`); the framework-bound activity must be verified through the
codebase's established pattern (WS10 corrective-corrective, WS11 T1) of extracting a
**pure, named seam** the source calls, and driving that seam's real decision path.
The source logic is correct; it is the *testability* that is missing.

## Deferred verification (the box cannot prove these)

- **The intent → `conversationId` field hop at runtime.** A plain JVM unit test
  (no Robolectric) cannot instantiate `ThreadActivity` or deliver a real `Intent`.
  The box verifies behaviorally that a specific `conversationId` drives the query
  (the seam) and structurally that `ThreadActivity.observeMessages` passes its
  `conversationId` to that seam. Whether the OS actually delivers the extra into the
  field on-device is a runtime check.
- **The thread *rendering* on-device** and **a real notification-shade quick
  reply** remain deferred exactly as in the original WS11 contract — this corrective
  does not touch those.

## Tasks

### T1 — Extract `ThreadMessageLoader` seam and behaviorally verify the conversationId → query data flow

The finding is a coverage gap: the WS11 data-flow wiring is present and correct in
source (`ThreadActivity.kt` reads the intent's `conversationId` and queries
`observeForConversation(conversationId)`), but it is inlined in a private,
framework-bound method that no test drives, so the scenario "launching with a
specific conversation ID and having that ID drive the data load" is unverified.
Answer it by extracting a pure, named seam that `ThreadActivity` calls as its *only*
load path, and writing a behavioral test that drives the seam by name.

Add `app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadMessageLoader.kt`: an `object`
with a single function
`observeForConversation(dao: MessageDao, conversationId: Long): Flow<List<Message>>`
that calls `dao.observeForConversation(conversationId)` and maps each emission's
entities to the pure `core` model via `Mappers.toMessage` (zero `android.*`
imports — JVM-testable, mirroring the WS11 T1 pure-logic pattern). Change
`ThreadActivity.observeMessages()` to call
`ThreadMessageLoader.observeForConversation(database.messageDao(), conversationId)`
and submit the result to the adapter. This makes the seam the *only* path the data
load uses, so the seam's behaviour is the wiring's behaviour.

Create `app/src/test/kotlin/com/piercingxx/txxt/ui/ThreadMessageLoaderTest.kt` — a
JVM unit test that calls `ThreadMessageLoader.observeForConversation` **by name**
with a mocked `MessageDao` (MockK, already a test dependency) and asserts behaviour:
(a) given `conversationId = 42L`, the loader calls `dao.observeForConversation(42L)`
(exactly once) and emits the mapped `core` `Message`s for that conversation; (b) a
different id (`7L`) queries that id; and (c) a wiring assertion reads
`ThreadActivity.kt` and confirms `observeMessages` routes the load through
`ThreadMessageLoader.observeForConversation` with `conversationId` (so the seam is
not dead code). This test would have failed before the fix because
`ThreadMessageLoader` did not exist (compile error) and nothing behaviorally
verified the conversationId → query flow.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.ui.ThreadMessageLoaderTest
- files: app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadMessageLoader.kt, app/src/main/kotlin/com/piercingxx/txxt/ui/ThreadActivity.kt, app/src/test/kotlin/com/piercingxx/txxt/ui/ThreadMessageLoaderTest.kt

## Final gate

The whole-workstream gate is the app module unit test suite — it must pass with the
new corrective test included:

- ./gradlew :app:testDebugUnitTest --offline

It must exit 0. No individual task claims this command as its verify; T1's verify is
the `ThreadMessageLoaderTest` node, and the Final gate confirms the whole suite
(121 existing tests plus the 3 corrective tests) holds together with the thread's
data flow wired. The on-device checks (the thread *rendering*, a real
notification-shade quick reply, an actual SMS transmission) remain deferred (see
Deferred verification) because the box has no device or carrier channel.