<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS15 — Failure-mode hardening (task contract)

Scope: the task contract for **WS15 — Failure-mode hardening** of the workstream
inventory at `contracts/TxxT.md:299`. It handles every row of the failure-mode
table at `docs/INSPIRATION.md:158` — say so on first launch if SMS permission is
denied and never silently fail to send; reconcile unread counts and pending
sends across a reboot; surface a block that matches a legit contact with a
reason and override; retry a failed MMS download with backoff and show the
failed state; make backup restore idempotent; and warn loudly if the
default-SMS-handler role is revoked.

Authoritative sources read this session: `docs/INSPIRATION.md:158` (the
failure-mode table), `docs/PRIVACY.md` (the privacy posture the behaviours must
not violate), and the sibling-repo conventions measured on disk.

## State of the tree (measured this session)

Measured on branch `main` (HEAD `e9d7ee2`), clean working tree. The project is
well past the scaffold — `core/` and `app/` modules build and the app-module
JVM suite is green. The failure-mode table has six rows; **three are not started
at all**, **two are partially done** (the reason/override slice and the backup
serializer), and **none is fully complete**.

**Gate report (run this session).** `./gradlew :app:testDebugUnitTest --offline`
exits 0 (`BUILD SUCCESSFUL`). The app-module test results report **171 tests, 0
failures, 0 errors** across 26 test classes (measured from
`app/build/test-results/testDebugUnitTest/*.xml`). The `core/` module compiles as
a dependency of `:app`. This gate passes today but **proves none of the WS15
deliverables** — none of the six failure-mode behaviours has a test.

| Failure-mode row (`docs/INSPIRATION.md:158`) | Status | Evidence (this session) |
|---|---|---|
| SMS permission denied → say so on first launch; never silently fail to send | **Not started** | `SendPipeline.sendSms` (`app/.../service/SendPipeline.kt:24-29`) calls `SmsManager.sendTextMessage` with no permission check and no error path; no `onRequestPermissionsResult`, no runtime-permission request, no first-launch prompt anywhere in `app/src/main` (searched: 0 matches) |
| Device reboot → reconcile unread counts and pending sends survive | **Not started** | `UnreadCount` (`core/.../UnreadCount.kt`) only derives counts — nothing persists or reconciles them across reboot; `SendState` (`core/.../SendState.kt`) has QUEUED/SENDING/FAILED states but no persistence and no reconcile; no `BOOT_COMPLETED` receiver in the manifest (`app/src/main/AndroidManifest.xml`, searched: 0 matches) |
| Blocking filter matches a legit contact → surface block with reason, allow override | **Partially done** | The **reason** exists: the filter's `evaluate` method returns a `BlockReason` with `canOverride=true` when a starred contact matches a rule (`app/.../block/InboundFilter.kt:53-64`, `app/.../block/BlockReason.kt:31-35`). The **override** does not: no persisted allow/override action and no UI affordance exist anywhere in `app/src/main` (searched `override` in `block/`: only the `canOverride` flag, no store) |
| MMS download fails → retry with backoff; show the failed state | **Not started** | `MmsReceiver` (`app/.../service/MmsReceiver.kt`) drops audio MMS and passes non-audio for storage; no download/retry/backoff/failed-state mechanism exists. `SendPolicy.autoDownloadMms()` is `false` (fetch only on explicit tap). Searched `backoff`: 0 matches |
| Backup restore conflicts → idempotent import; re-import is a no-op | **Partially done** | The **serializer** is deterministic so a re-import is a no-op at the parse level (`core/.../BackupSerializer.kt:15-16`, `BackupData` round-trips losslessly). The **restore path** does not exist: no `restore` in `app/src/main` (searched: 0 matches), no import that writes the parsed `BackupData` into Room |
| Default SMS handler revoked → warn loudly | **Not started** | No `isDefaultSmsApp`, no `Telephony.Sms.getDefaultSmsApplication`, no warning surface anywhere in `app/src/main` (searched: 0 matches) |

**Deferred verification (the box cannot prove these).** Each row's required
behaviour is ultimately an on-device check (a real send with permission denied,
a real reboot, a real MMS download failure, a real role revocation). The box
verifies the JVM-testable logic — the decision, state, persistence, and warning
surfaces — and the operator confirms the on-device behaviour. Nothing here
touches the network; the no-`INTERNET` manifest claim (`AndroidManifest.xml:24-25`)
is preserved, not re-implemented.

## Tasks

### T1 — Say so on first launch when SMS permission is denied; never silently fail to send

The send pipeline today hands every message to `SmsManager.sendTextMessage`
unconditionally (`SendPipeline.kt:24-29`) — if `SEND_SMS` is denied, the send
fails silently. Add a `PermissionGate` that the send path consults before
sending: when `SEND_SMS` is not granted it returns a denied result instead of
calling `SmsManager`, and the app surfaces a first-launch explanation (a
permission request/prompt) so the user is told why the message did not send.
The gate must be a seam the send pipeline actually calls — a test drives the
pipeline with a denied gate and asserts the send is refused (not silently
dropped) and a denied result is produced. `SendPolicy.requestsDeliveryReport()`
stays `false` (`SendPolicy.kt:22`); the gate adds permission handling, it does
not change the privacy posture.

- verify: ./gradlew :app:testDebugUnitTest --tests com.piercingxx.txxt.service.PermissionGateTest
- files: app/src/main/kotlin/com/piercingxx/txxt/service/PermissionGate.kt, app/src/main/kotlin/com/piercingxx/txxt/service/SendPipeline.kt, app/src/test/kotlin/com/piercingxx/txxt/service/PermissionGateTest.kt

### T2 — Reconcile unread counts and pending sends across a reboot

Nothing persists unread counts or in-flight sends across a reboot: `UnreadCount`
only derives totals (`UnreadCount.kt`) and `SendState` has QUEUED/SENDING/FAILED
states with no storage (`SendState.kt`). Add a `RebootReconcile` that, on a
`BOOT_COMPLETED` receipt, reloads the persisted send state and unread counts,
re-derives the unread total from the stored messages, and re-queues any
message left in QUEUED/SENDING (so a pending send survives the reboot). The
receiver is declared in the manifest. A test drives the reconcile with a
persisted pending send and a persisted unread count and asserts both survive —
the test fails if the reconcile never runs or drops the state.

- verify: ./gradlew :app:testDebugUnitTest --tests com.piercingxx.txxt.service.RebootReconcileTest
- files: app/src/main/kotlin/com/piercingxx/txxt/service/RebootReconcile.kt, app/src/main/AndroidManifest.xml, app/src/test/kotlin/com/piercingxx/txxt/service/RebootReconcileTest.kt

### T3 — Surface a block that matches a legit contact with a reason and an override

The reason half exists: a starred-contact rule match returns `BlockReason` with
`canOverride=true` (`InboundFilter.kt:53-64`, `BlockReason.kt:31-35`). The
override half does not. Add a `BlockOverrideStore` that persists an explicit
allow for a sender, and wire `InboundFilter` so a persisted override delivers a
message the filter would otherwise block — the override beats the block. A test
asserts that once a sender is in the store, an otherwise-blocked message is
delivered, and that the reason is still surfaced with `canOverride=true` so the
UI can show the affordance. The existing `InboundFilterTest` covers the reason;
this task adds the override seam and its own test file.

- verify: ./gradlew :app:testDebugUnitTest --tests com.piercingxx.txxt.block.BlockOverrideTest
- files: app/src/main/kotlin/com/piercingxx/txxt/block/BlockOverrideStore.kt, app/src/main/kotlin/com/piercingxx/txxt/block/InboundFilter.kt, app/src/test/kotlin/com/piercingxx/txxt/block/BlockOverrideTest.kt

### T4 — Retry a failed MMS download with backoff and show the failed state

`MmsReceiver` drops audio MMS and passes non-audio for storage but has no
download/retry mechanism (`MmsReceiver.kt:53-75`); `SendPolicy.autoDownloadMms()`
is `false` so remote content is fetched only on explicit tap (`SendPolicy.kt:37`).
Add an `MmsDownloadRetry` that, when an explicit-tap MMS download fails, records
a failed state and schedules a retry with exponential backoff (a bounded retry
count, then a terminal failed state the UI can display). A test drives the retry
scheduler with a failing download and asserts the backoff grows, the retries are
bounded, and the terminal state is FAILED (never a silent drop).

- verify: ./gradlew :app:testDebugUnitTest --tests com.piercingxx.txxt.service.MmsDownloadRetryTest
- files: app/src/main/kotlin/com/piercingxx/txxt/service/MmsDownloadRetry.kt, app/src/test/kotlin/com/piercingxx/txxt/service/MmsDownloadRetryTest.kt

### T5 — Make backup restore idempotent

The serializer is deterministic so a re-import is a no-op at the parse level
(`BackupSerializer.kt:15-16`), but there is no restore path that writes the
parsed `BackupData` into the data layer (`BackupJson.kt` only serializes/
deserializes; no `restore` exists in `app/src/main`). Add a `RestoreService`
that imports a `BackupData` into Room such that importing the same payload twice
leaves the store unchanged — re-import is a no-op. A test imports the same
payload twice and asserts the second import changes nothing (message set,
settings, blocklist, starred all identical).

- verify: ./gradlew :app:testDebugUnitTest --tests com.piercingxx.txxt.data.RestoreIdempotencyTest
- files: app/src/main/kotlin/com/piercingxx/txxt/data/RestoreService.kt, app/src/test/kotlin/com/piercingxx/txxt/data/RestoreIdempotencyTest.kt

### T6 — Warn loudly when the default-SMS-handler role is revoked

Nothing checks the default-SMS-handler role anywhere in `app/src/main` (searched
`isDefaultSmsApp`: 0 matches). Add a `DefaultHandlerMonitor` that checks whether
the app is still the default SMS handler (via the platform SMS role/application
API) and, when the role is revoked, produces a warning the app surfaces loudly —
the app is inert without the role. A test drives the monitor with a revoked-role
condition and asserts a warning is produced; the test fails if the monitor never
runs or never warns.

- verify: ./gradlew :app:testDebugUnitTest --tests com.piercingxx.txxt.service.DefaultHandlerWarningTest
- files: app/src/main/kotlin/com/piercingxx/txxt/service/DefaultHandlerMonitor.kt, app/src/test/kotlin/com/piercingxx/txxt/service/DefaultHandlerWarningTest.kt

## Final gate

The whole-workstream gate is the repo's own app-module test command, which must
pass with all six WS15 test classes green:

- verify: ./gradlew :app:testDebugUnitTest --offline

It must exit 0. No individual task claims this command as its verify; each task
runs its own named test class, and this gate closes the workstream by running
the whole app-module suite together.