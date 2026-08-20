<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS3 — Core: blocking filters & starred call-through (task contract)

Scope: the task contract for **WS3 — Core: blocking filters & starred call-through**
of the workstream inventory at `contracts/TxxT.md:87`. It builds the pure-Kotlin
blocking domain in `core/` with zero `android.*` imports — `BlockingFilter`,
keyword/phrase matching, the unknown-sender rule, the blocklist model, and the
starred-contacts bypass that lets a starred contact through every suppression
(`docs/PRIVACY.md:106`), with a block rule that would match a starred contact
surfaced with a reason rather than applied silently (`docs/INSPIRATION.md:162`) —
so the logic that must be correct is JVM-testable without a device.

The goal is **not stale and not started** on this branch
(`laundry-bot/queue-TxxT-ws2-corrective`, HEAD `646f04a`). Measured this session:
the `core/` module exists and is green (75 tests, 0 failures), but there is **no
blocking-domain code anywhere in it** — `BlockingFilter` appears only as prose in
`contracts/TxxT.md:89` and `docs/INSPIRATION.md:162`, and a search for `block` and
`starred` across `core/` returns zero source matches. Every deliverable the WS3
goal names is **not started**; this contract scopes the full build of the domain.

Authoritative sources read this session: `contracts/TxxT.md:87` (the WS3 goal),
`docs/PRIVACY.md:105-121` (starred call-through), `docs/PRIVACY.md:164` (unknown
senders → quarantine unless starred), `docs/INSPIRATION.md:154-165` (failure-mode
table), and the existing `core/` sources and tests measured on disk.

## State of the tree (measured this session)

Measured on branch `laundry-bot/queue-TxxT-ws2-corrective` (HEAD `646f04a`),
working tree clean. The `core/` module is fully scaffolded and green from WS2, but
contains **no blocking-domain code**. Every deliverable the WS3 goal names is
**not started**; the goal is not stale.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| Pure-Kotlin `core/` module wired into the Gradle build | **Already done (WS2)** | `settings.gradle:18` includes `':core'`; `core/build.gradle:1-21` applies `org.jetbrains.kotlin.jvm`, JVM target 1.8, JUnit 4.13.2 |
| `BlockingFilter` | **Not started** | search for `BlockingFilter` across the tree returns only prose in `contracts/TxxT.md:89` and `docs/INSPIRATION.md:162`; zero source files under `core/` |
| keyword/phrase matching | **Not started** | search for `block` under `core/` returns zero source matches |
| unknown-sender rule | **Not started** | search for `block` under `core/` returns zero source matches; the rule's intent is at `docs/PRIVACY.md:164` (unknown senders → quarantine unless starred) |
| blocklist model | **Not started** | search for `block` under `core/` returns zero source matches |
| starred-contacts bypass (through every suppression) | **Not started** | search for `starred` under `core/` returns zero source matches; the requirement is at `docs/PRIVACY.md:106-121` |
| block rule matching a starred contact surfaced with a reason | **Not started** | the requirement is at `docs/INSPIRATION.md:162` (surface with a reason, allow override); no source implements it |
| JVM suite covers keyword/unknown-sender/starred blocking | **Not started** | the 7 existing test files cover the WS2 domain only (see gate report); no blocking test exists |

**Gate report (run this session).** `./gradlew :core:test --offline --rerun-tasks`
with `JAVA_HOME=/home/piercingxx/.local/android-toolchain/jdk17` → **BUILD
SUCCESSFUL in 2s**; `:core:test` executed fresh. Test report
(`core/build/test-results/test/*.xml`, this session): ConversationListTest 7,
MessageModelTest 10, PinSortArchiveTest 10, ReceiveStateTest 16,
ScheduleDelayTest 14, SendStateMachineTest 13, UnreadCountTest 5 — **75 tests, 0
failures, 0 errors, 0 skipped**. The no-`android.*` gate
(`python3 scripts/verify_no_android_imports.py`) exits 0 this session: "no
android.* imports in 15 Kotlin source files under core/". These 75 tests cover the
WS2 domain only; none exercise blocking, so the WS3 `done when` is unproven until
the new tests land.

## Deferred verification (the box cannot prove these)

- **Nothing behavioural is deferred.** The gate runs green on this box with real
  numbers and the WS3 `done when` is fully machine-checkable — the new blocking
  tests run under `./gradlew :core:test`. What is *out of WS3's scope*: the
  notification-level suppressions (quiet hours, notification redaction,
  sender-name-only) that the starred bypass also overrides are WS8's domain
  (`contracts/TxxT.md:155`), and the *wiring* of the WS3 filters into the inbound
  message path (routing unknown senders to a quarantine view, applying the filters
  to live inbound messages, surfacing an override UI) is WS9's domain
  (`contracts/TxxT.md:171`). WS3 proves the pure decision logic in `core/`; the
  on-device application of that logic is the later workstreams' scope.
- **The "every suppression" breadth.** `docs/PRIVACY.md:112-115` lists the starred
  bypass as covering blocking filters, keyword/unknown-sender blocking, quiet
  hours, and notification redaction. Of those, the suppressions that exist in the
  WS3 `core/` domain are keyword/phrase, unknown-sender, and blocklist blocking.
  Quiet hours and notification redaction are not `core/` logic in this workstream,
  so the WS3 starred bypass is proven against **every suppression that exists in
  the WS3 core domain**; the notification-level suppressions are verified in WS8.

## Tasks

### T1 — `BlockingFilter` with keyword and phrase matching

Build the central filter abstraction `BlockingFilter` in `core/` — a pure-Kotlin
value type with zero `android.*` imports, matching the style of the existing
`core/` model (see `ConversationFlags.kt:20-50`). It evaluates an inbound message
body against a set of blocking rules and returns a decision. The keyword/phrase
matching must match and reject correctly: a body containing a listed keyword is
blocked; a body containing a listed phrase (multi-word, case-insensitive) is
blocked; a body that contains none of the listed terms is **not** blocked; the
match is case-insensitive and matches on word/phrase boundaries, not as a naive
substring that would false-positive on a longer word (e.g. the keyword "win" must
not block "winter"). The test file `BlockingFilterTest.kt` proves each of these
match/reject cases. This task owns the filter's keyword/phrase matching behaviour
only; the blocklist model that *stores* the rules is T3, and the unknown-sender
rule is T2 — each has its own test file so the verifies cannot be confused.

- verify: ./gradlew :core:test --offline --tests com.piercingxx.txxt.core.BlockingFilterTest
- files: core/src/main/kotlin/com/piercingxx/txxt/core/BlockingFilter.kt, core/src/test/kotlin/com/piercingxx/txxt/core/BlockingFilterTest.kt

### T2 — Unknown-sender rule

Build the unknown-sender rule in `core/` — a pure-Kotlin type that decides whether
an inbound message's sender is subject to the unknown-sender suppression. Per
`docs/PRIVACY.md:164`, messages from non-contacts go to a quarantine view rather
than the main thread list unless the sender is starred. The rule takes a sender
address and the set of known (contact) addresses plus the set of starred addresses
and returns whether the sender is unknown and therefore suppressed: a sender not
in the known-contacts set is unknown and suppressed; a known contact is **not**
suppressed by this rule; a starred sender is **never** suppressed by this rule
regardless of known/unknown status (the starred bypass, which T4 owns, must be
respected here). The test file `UnknownSenderRuleTest.kt` proves each case. This
task owns the unknown-sender decision only; it does not own the broader starred
bypass (T4) or the blocklist model (T3).

- verify: ./gradlew :core:test --offline --tests com.piercingxx.txxt.core.UnknownSenderRuleTest
- files: core/src/main/kotlin/com/piercingxx/txxt/core/UnknownSenderRule.kt, core/src/test/kotlin/com/piercingxx/txxt/core/UnknownSenderRuleTest.kt

### T3 — Blocklist model

Build the blocklist model in `core/` — a pure-Kotlin value type holding the set of
blocking rules the filters apply (keyword and phrase entries), with add/remove and
membership semantics. It is the persistence-shaped model that WS5's backup
serialization (`contracts/TxxT.md:114`, blocklist included per
`docs/PRIVACY.md:110`) and WS9's wiring will consume, so it must be a plain value
type with no `android.*` imports. The model supports: adding a keyword/phrase rule,
removing a rule, listing the current rules, and answering whether a given term is
currently blocked. The test file `BlocklistTest.kt` proves add/remove/membership
behaviour (adding then removing a rule leaves it unblocked; a never-added rule is
not blocked; the rule list reflects the adds/removes). This task owns the *storage
and membership* of the blocklist only; the *matching behaviour* of the rules it
holds is T1's filter, which has its own test file.

- verify: ./gradlew :core:test --offline --tests com.piercingxx.txxt.core.BlocklistTest
- files: core/src/main/kotlin/com/piercingxx/txxt/core/Blocklist.kt, core/src/test/kotlin/com/piercingxx/txxt/core/BlocklistTest.kt

### T4 — Starred-contacts bypass

Build the starred-contacts bypass in `core/` — the policy that lets a starred
contact through **every suppression** in the WS3 core domain. Per
`docs/PRIVACY.md:106-121`, starred is a first-class contact flag and starred
contacts' messages bypass every suppression — blocking filters, keyword/unknown
sender blocking — while unstarred contacts keep the full suppression posture
(`docs/PRIVACY.md:121`). The bypass is a pure-Kotlin policy function that takes a
sender and the starred set plus a suppression decision and returns the effective
decision: when the sender is starred, every core suppression is lifted (a keyword
match, an unknown-sender hit, and a blocklist hit all pass through); when the
sender is unstarred, the full suppression posture is kept (the same keyword /
unknown-sender / blocklist hits are all applied). The test file `StarredBypassTest.kt`
proves: a starred contact passes a keyword match, passes an unknown-sender hit, and
passes a blocklist hit, while the same three hits on an unstarred sender are all
suppressed. This task owns the bypass policy; the *reason surfacing* when a rule
would match a starred contact is T5's distinct behaviour, with its own test file.

- verify: ./gradlew :core:test --offline --tests com.piercingxx.txxt.core.StarredBypassTest
- files: core/src/main/kotlin/com/piercingxx/txxt/core/StarredBypass.kt, core/src/test/kotlin/com/piercingxx/txxt/core/StarredBypassTest.kt

### T5 — Surface a reason when a rule would match a starred contact

Build the decision-surfacing behaviour in `core/` — per `docs/INSPIRATION.md:162`
(a "blocking filter matches a legit contact" must "surface the block with a
reason, allow override") and `docs/PRIVACY.md:116-118` (a block rule that would
match a starred contact is surfaced with a reason and does not apply silently).
The decision type distinguishes a **silently-applied** block from a **surfaced**
block: when a rule would match a **starred** contact, the decision is *not*
applied silently — it is surfaced with a human-readable reason naming the rule and
the fact that the contact is starred, so the UI (WS9) can offer an override; when
the same rule matches an **unstarred** contact, the block applies normally without
that surfacing. The test file `BlockDecisionTest.kt` proves: a rule matching a
starred contact produces a surfaced decision carrying a reason and is **not**
applied; a rule matching an unstarred contact produces a silently-applied block.
This task owns the reason-surfacing decision only; the bypass that *lifts* the
suppression for starred contacts is T4's distinct behaviour, with its own test file.

- verify: ./gradlew :core:test --offline --tests com.piercingxx.txxt.core.BlockDecisionTest
- files: core/src/main/kotlin/com/piercingxx/txxt/core/BlockDecision.kt, core/src/test/kotlin/com/piercingxx/txxt/core/BlockDecisionTest.kt

## Final gate

The whole-workstream gate is the repo's own `core/` test command — it compiles the
module and runs the full JVM suite (the WS2 75 tests plus the new WS3 blocking
tests):

- ./gradlew :core:test --offline

It must exit 0 (75+ tests, 0 failures), and the existing no-`android.*` gate
(`python3 scripts/verify_no_android_imports.py`, which already exits 0 over the 15
existing `core/` sources and must continue to pass over the new blocking sources)
keeps the "no `android.*` imports" claim true. No individual task claims this
command as its verify; each task's verify is its own single-node test selector, and
the Final gate confirms the whole suite holds together with the new blocking tests
included.