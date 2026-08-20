<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS2 — Core: message model & state machine (task contract)

Scope: the task contract for **WS2 — Core: message model & state machine** of the
workstream inventory at `contracts/TxxT.md:74`. It builds the pure-Kotlin message
domain in `core/` with zero `android.*` imports — the SMS/MMS message and
conversation/thread model, send/receive state, pending/delayed/scheduled-message
states, unread counts, and the pinning / sorting / archiving flags — so the logic
that must be correct is JVM-testable without a device.

The goal is **mostly already done** on this branch (`laundry-bot/queue-TxxT-ws2-corrective`,
HEAD `f3572b2`). Measured this session: the full `core/` module exists, the gate
`./gradlew :core:test` passes with **75 tests, 0 failures**, and the no-`android.*`
imports gate passes over 15 Kotlin source files. One deliverable named by the goal
is **not actually complete**: the pinning flag is not wired into the conversation-list
ordering — `PINNED_FIRST` does not put pinned conversations first. This contract
scopes only that gap.

Authoritative sources read this session: `contracts/TxxT.md:74` (the WS2 goal),
`docs/DESIGN.md`, `docs/PRIVACY.md`, and the existing `core/` sources and tests
measured on disk.

## State of the tree (measured this session)

Measured on branch `laundry-bot/queue-TxxT-ws2-corrective` (HEAD `f3572b2`),
working tree clean. The `core/` module is fully scaffolded and substantially
implemented. Every deliverable the WS2 goal names was checked against the tree;
nearly all are **already done**.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| Pure-Kotlin `core/` module wired into the Gradle build | **Already done** | `settings.gradle:18` includes `':core'`; `core/build.gradle:1-21` applies `org.jetbrains.kotlin.jvm`, JVM target 1.8, JUnit 4.13.2 |
| SMS/MMS message model | **Already done** | `core/src/main/kotlin/com/piercingxx/txxt/core/Message.kt:6-43` — `MessageDirection`, `MessageTransport`, `Message` (id, conversationId, direction, transport, body, timestamp, senderAddress, isRead, `isUnread`) |
| conversation/thread model | **Already done** | `core/src/main/kotlin/com/piercingxx/txxt/core/Conversation.kt:10-37` — `Conversation` (participants, messages, `latestMessage`, `latestTimestampMillis`, `unreadCount`, `hasUnread`) |
| send state | **Already done** | `core/src/main/kotlin/com/piercingxx/txxt/core/SendState.kt:11-62` — `SendState` enum + `SendStateMachine` (validated transitions, terminal at SENT; no delivery-report state per `docs/PRIVACY.md:23`) |
| receive state | **Already done** | `core/src/main/kotlin/com/piercingxx/txxt/core/ReceiveState.kt:13-74` — `ReceiveState` enum + `ReceiveStateMachine` (MMS auto-download OFF, audio-MMS DROPPED per `docs/PRIVACY.md:151`, `:91`) |
| pending/delayed/scheduled-message states | **Already done** | `core/src/main/kotlin/com/piercingxx/txxt/core/ScheduledMessage.kt:14-98` — `ScheduledState` (PENDING/DELAYED/SCHEDULED/CANCELLED) + `ScheduledMessage` (`sendAtMillis`, `isDue`) + `ScheduledMessageStateMachine` |
| unread counts | **Already done** | `core/src/main/kotlin/com/piercingxx/txxt/core/UnreadCount.kt:11-34` — `total`, `perConversation`, `anyUnread` |
| pinning / sorting / archiving flags | **Model done; sorting wiring NOT done** | `core/src/main/kotlin/com/piercingxx/txxt/core/ConversationFlags.kt:27-50` — `isPinned`/`isArchived`/`sortOrder` with immutable transitions. **But** `ConversationList.sorted` (`ConversationList.kt:22-44`) takes only `(conversations, order)` — it never reads `isPinned`, and its `PINNED_FIRST` branch (`ConversationList.kt:38-41`) sorts by unread count, not by pin state. Pinning is not wired into the ordering. |
| JVM suite covers sending | **Already done** | `core/src/test/kotlin/com/piercingxx/txxt/core/SendStateMachineTest.kt` — 13 tests |
| JVM suite covers receiving | **Already done** | `core/src/test/kotlin/com/piercingxx/txxt/core/ReceiveStateTest.kt` — 16 tests |
| JVM suite covers scheduling/delaying | **Already done** | `core/src/test/kotlin/com/piercingxx/txxt/core/ScheduleDelayTest.kt` — 14 tests |
| JVM suite covers unread-count derivation | **Already done** | `core/src/test/kotlin/com/piercingxx/txxt/core/UnreadCountTest.kt` (5) + `ConversationListTest.kt` (7) |
| JVM suite covers pin/sort/archive transitions | **Model transitions done; PINNED_FIRST ordering not covered** | `PinSortArchiveTest.kt` — 10 tests cover the `ConversationFlags` transitions. **No test proves a pinned conversation sorts before an unpinned one** under `PINNED_FIRST`; `ConversationListTest.kt:121` (`pinned first defaults to unread then recency`) actually asserts the current non-pinning behaviour. |
| `core/` compiles with no `android.*` imports | **Already done** | `python3 scripts/verify_no_android_imports.py` exits 0: "no android.* imports in 15 Kotlin source files under core/" (this session) |

**Gate report (run this session).** `./gradlew :core:test --offline --rerun-tasks`
with `JAVA_HOME=/home/piercingxx/.local/android-toolchain/jdk17` → **BUILD
SUCCESSFUL in 9s**; `:core:test` executed fresh. Test report
(`core/build/test-results/test/*.xml`, timestamp `2026-08-20T20:45:41`):
ConversationListTest 7, MessageModelTest 10, PinSortArchiveTest 10,
ReceiveStateTest 16, ScheduleDelayTest 14, SendStateMachineTest 13,
UnreadCountTest 5 — **75 tests, 0 failures, 0 errors, 0 skipped**. The
no-`android.*` gate and the existing structural verifies
(`verify_core_module.py`, `verify_unread_count_wired.py`) all exit 0 this session.

**The one gap.** The WS2 goal names "the pinning / sorting / archiving flags" and
`done when` requires "pin/sort/archive transitions" to be covered and pass. The
flags model and its transitions are done, but the **pin flag is not wired into the
list ordering**: `ConversationList.sorted` ignores `isPinned`, so `PINNED_FIRST`
does not actually put pinned conversations first. That is the only deliverable
that is not complete.

## Deferred verification (the box cannot prove these)

- **Nothing behavioural is deferred.** The gate runs green on this box with real
  numbers (75 tests, 0 failures), so the `done when` behavioural claims are
  machine-verified. The on-device rendering of the conversation list (WS10) is a
  later workstream's scope, not WS2's.

## Tasks

### T1 — Wire the pin flag into the conversation-list ordering

The WS2 goal names the pinning flag as a deliverable, but `ConversationList.sorted`
(`core/src/main/kotlin/com/piercingxx/txxt/core/ConversationList.kt:22`) takes only
`(conversations, order)` and its `PINNED_FIRST` branch (`ConversationList.kt:38`)
sorts by unread count, never consulting `ConversationFlags.isPinned`
(`ConversationFlags.kt:29`). Fix the ordering so `PINNED_FIRST` actually puts
pinned conversations first (pinned before unpinned, then by recency), and prove it
with a test that fails before the fix. Change `ConversationList.sorted` to accept
the pinned state (e.g. a `Map<Long, Boolean>` or `Set<Long>` of pinned
conversation ids, defaulting to empty so existing call sites still compile) and
make the `PINNED_FIRST` branch order pinned-first-then-recency. Add a dedicated
JVM test file `PinnedFirstOrderingTest.kt` that builds a pinned and an unpinned
conversation and asserts the pinned one sorts first under `PINNED_FIRST` — a test
that fails on the current code and passes after the fix. Reconcile the existing
`pinned first defaults to unread then recency` test in `ConversationListTest.kt:121`
so its expectation matches the corrected pinned-first behaviour (pinning now
winning over unread). The verify is the new test file's node id, so it uniquely
proves this task and cannot be confused with any other.

- verify: ./gradlew :core:test --offline --tests com.piercingxx.txxt.core.PinnedFirstOrderingTest
- files: core/src/main/kotlin/com/piercingxx/txxt/core/ConversationList.kt, core/src/test/kotlin/com/piercingxx/txxt/core/PinnedFirstOrderingTest.kt, core/src/test/kotlin/com/piercingxx/txxt/core/ConversationListTest.kt

## Final gate

The whole-workstream gate is the repo's own `core/` test command — it compiles the
module and runs the full JVM suite:

- ./gradlew :core:test --offline

It must exit 0 (75+ tests, 0 failures). No individual task claims this command as
its verify; T1's verify is the single-node `PinnedFirstOrderingTest` selector, and
the Final gate confirms the whole suite holds together with the new pinning test
included.