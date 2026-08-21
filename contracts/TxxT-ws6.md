<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS6 — Data layer (Room + Gson) (task contract)

Scope: the task contract for **WS6 — Data layer (Room + Gson)** of the workstream
inventory at `contracts/TxxT.md:128`. It writes the Android data layer in `data/`
— the Room entities, DAOs, `TxxTDatabase`, the mappers between the pure `core/`
model and Room storage, the starred-contact flag persisted, and the schema
exported and committed — wired to Gson for the backup JSON
(`docs/INSPIRATION.md:132`, `docs/DESIGN.md:86`).

`done when`: the `data/` classes compile against the `core/` model, the Room
schema is exported and committed, and the pure mapper slice between the core
model and storage has a JVM unit test that passes; the Room wiring itself is
device-verified (see Deferred).

Authoritative sources read this session: `contracts/TxxT.md:128-138` (the WS6
goal and `done when`), `docs/INSPIRATION.md:141-150` (the package layout — `data/`
holds "entities, DAOs, TxxTDatabase, BackupJson"), `docs/DESIGN.md:86` (the
Room + Gson stack), the existing `core/` model sources and tests measured on
disk, `app/build.gradle` (Room 2.6.1 + kapt schema export already configured),
and the offline Gradle dependency cache (Room, Gson, coroutines all present).

## State of the tree (measured this session)

Measured on branch `laundry-bot/queue-TxxT-ws5-i3` (HEAD `c627683`), working tree
clean. The pure `core/` model is fully built and green from WS2–WS5; the `data/`
layer is **not started** — there is no `data/` package, no `app/src/main/kotlin/`
directory at all, and no Room schema. Every deliverable the WS6 goal names is
**not started** except the Room/Gson build wiring, which WS1 already put in place.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| The pure `core/` model to map to/from | **Already done (WS2–WS5)** | `core/src/main/kotlin/com/piercingxx/txxt/core/Conversation.kt:10-37`, `Message.kt:28-43`, `ConversationFlags.kt:27-50`, `BackupData.kt:20-35`, `BackupSerializer.kt:20-104` — all pure Kotlin, zero `android.*` imports |
| Room + Gson wired into the app build | **Already done (WS1)** | `app/build.gradle:62-67` declares `androidx.room:room-runtime/room-ktx/room-compiler:2.6.1` and `com.google.code.gson:gson:2.10.1`; `app/build.gradle:47-53` already sets `room.schemaLocation` to `$projectDir/schemas` |
| `data/` Room entities | **Not started** | no `app/src/main/kotlin/` directory exists (measured: `find app/src -type f` returns only `AndroidManifest.xml` and the `res/` icon files); no entity class anywhere |
| DAOs | **Not started** | no DAO interfaces exist |
| `TxxTDatabase` | **Not started** | no database class exists |
| Mappers between `core/` model and Room storage | **Not started** | no mapper code exists; this is the JVM-testable slice the `done when` requires |
| Starred-contact flag persisted | **Not started** | no conversation entity column exists; the starred concept lives in `core/` only as `BackupData.starred: List<String>` (`BackupData.kt:34`) |
| Room schema exported and committed | **Not started** | no `app/schemas/` directory exists (measured: `ls app/schemas` → "no app/schemas dir"); the export config is present (`app/build.gradle:47-53`) but nothing has generated a schema yet |
| Wired to Gson for the backup JSON | **Not started** | `core/BackupSerializer.kt` is a self-contained JSON writer with no Gson dependency; the Gson-backed data-layer adapter does not exist |

**Gate report (run this session).** The existing `core/` suite is green:
`./gradlew :core:test --offline` → **BUILD SUCCESSFUL in 1s**. Test report
(`core/build/test-results/test/*.xml`, this session): BackupExportTest 8,
ConversationListTest 7, MessageModelTest 10, PinSortArchiveTest 10,
ReceiveStateTest 16, ScheduleDelayTest 14, SendStateMachineTest 13,
UnreadCountTest 5 — **83 tests, 0 failures, 0 errors, 0 skipped** across 8 test
files. The `app/` module configures and its JVM unit-test task is runnable
offline: `./gradlew :app:testDebugUnitTest --offline --dry-run` → **BUILD
SUCCESSFUL in 1s**, task graph includes `:app:kaptDebugKotlin`,
`:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`,
`:app:testDebugUnitTest` (all SKIPPED only because no source exists yet). The
app module's declared dependencies are all present in the offline cache
(measured under `/home/piercingxx/.gradle/caches/modules-2/files-2.1/`:
`androidx.room`, `androidx.sqlite`, `com.google.code.gson/gson`,
`org.jetbrains.kotlinx/kotlinx-coroutines-android`, `junit/junit`,
`com.google.android.material/material`), so once the `data/` sources land the
`:app:testDebugUnitTest` gate is expected to run green on this box.

**Toolchain note (measured this session).** The interactive shell here did not
have `JAVA_HOME`/`ANDROID_HOME` set, so the gates above were run with the env
prefix `JAVA_HOME=/home/piercingxx/.local/android-toolchain/jdk17
ANDROID_HOME=/home/piercingxx/.local/android-toolchain/sdk` (the jdk17 and SDK
measured at `/home/piercingxx/.local/android-toolchain/`, which holds `gradle-8.7`,
`jdk17`, and an SDK with `build-tools`, `platforms`, `platform-tools`,
`cmdline-tools`, `licenses`). The task guarantee is that JAVA_HOME (jdk17) and
ANDROID_HOME are injected into every verify at execution time, so the bare
verify lines below rely on that injection — the same pattern the WS2–WS4
contracts use.

## Deferred verification (the box cannot prove these)

- **The Room wiring itself (entities/DAOs/database against a real SQLite
  database).** `done when` explicitly defers this: "the Room wiring itself is
  device-verified". The JVM suite proves the *mappers* are pure and correct and
  that the `data/` classes *compile*; whether a real `TxxTDatabase` instance
  opens, migrates, and persists rows correctly on a GrapheneOS Pixel 9 is an
  on-device check that is the operator's (WS10/WS11 consume the DAOs).
- **The schema's on-device correctness.** The box proves the schema JSON is
  generated by the Room compiler and committed; whether that schema is a sound
  representation of the on-device database (migration-correct across app
  upgrades) is exercised on-device, not by the JVM suite.
- **The schema being committed.** T1's verify generates the schema JSON into
  `app/schemas/` as a build artifact; the commit of that generated file happens
  as part of the delivery loop (the same way every other task's files are
  committed). The box proves the file exists after the build; the git commit is
  the delivery step.
- **Gson byte-for-byte compatibility with the launcher's literal backup file.**
  The box proves a Gson round-trip of `BackupData` is lossless in-app. Whether
  the emitted JSON is byte-for-byte identical to the xx-launcher's own backup
  file is a cross-repo comparison the box cannot make (the launcher source is a
  separate repo) — the operator owns that check, as the WS5 contract already
  deferred it.

## Tasks

### T1 — Room entities, DAOs, TxxTDatabase, and the exported schema

Build the Room layer in `data/` under `app/src/main/kotlin/com/piercingxx/txxt/data/`,
matching the package layout at `docs/INSPIRATION.md:146`. Create the entity data
classes — `ConversationEntity` carrying a `isStarred: Boolean` column (the
starred-contact flag the goal names, mapping to the starred concept in
`BackupData.starred`) and `MessageEntity` carrying the message fields from
`core/Message.kt:28-43` (id, conversationId, direction, transport, body,
timestampMillis, senderAddress, isRead) — plus the DAO interfaces
(`ConversationDao`, `MessageDao`) and `TxxTDatabase` (a `@Database` class listing
the entities at `version = 1`, exposing the DAOs). The `app/build.gradle:47-53`
kapt `room.schemaLocation` is already configured, so compiling this database
class makes the Room compiler export the schema JSON into `app/schemas/`. The
verify is the app-module compile, which (a) proves the `data/` classes compile
against the `core/` model (the first `done when` clause) and (b) runs the Room
kapt compiler that generates the schema artifact. This task owns the Room
entities/DAOs/database and the schema generation; the pure mappers are T2 and the
Gson backup wiring is T3, each with its own test file so the verifies cannot be
confused. The schema JSON generated by this build must be committed with the
task's files (delivery step).

- verify: ./gradlew :app:compileDebugKotlin --offline
- files: app/src/main/kotlin/com/piercingxx/txxt/data/ConversationEntity.kt, app/src/main/kotlin/com/piercingxx/txxt/data/MessageEntity.kt, app/src/main/kotlin/com/piercingxx/txxt/data/ConversationDao.kt, app/src/main/kotlin/com/piercingxx/txxt/data/MessageDao.kt, app/src/main/kotlin/com/piercingxx/txxt/data/TxxTDatabase.kt, app/schemas/com.piercingxx.txxt.data.TxxTDatabase/1.json

### T2 — Pure mappers between the `core/` model and Room storage

Build the pure mapper slice in `data/` — the functions that convert between the
`core/` value types (`Conversation`, `Message`, `ConversationFlags`) and the Room
entity/DAO types (`ConversationEntity`, `MessageEntity`), including the
starred-contact flag (`ConversationEntity.isStarred` ↔ the starred state the goal
names). The mappers are plain Kotlin functions with no Android runtime calls, so
they are JVM-testable in the app module's unit tests. The test file
`MapperTest.kt` in `app/src/test/kotlin/com/piercingxx/txxt/data/` constructs
`core/` models, maps them to entities and back, and asserts the round-trip
preserves every field (id, conversationId, direction, transport, body,
timestamp, senderAddress, isRead, isStarred) — proving the pure mapper slice
between the core model and storage works, which is the third `done when` clause.
This task owns the mappers only; the entities/DAOs/database they map to are T1's
and the Gson backup wiring is T3, each with its own verify so the verifies cannot
be confused.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.data.MapperTest
- files: app/src/main/kotlin/com/piercingxx/txxt/data/Mappers.kt, app/src/test/kotlin/com/piercingxx/txxt/data/MapperTest.kt

### T3 — Gson wiring for the backup JSON

Build the Gson-backed adapter in `data/` that serializes and deserializes the
backup payload using Gson 2.10.1 (`app/build.gradle:67`), wired to the `core/`
backup model (`BackupData`, `BackupMessage` at `BackupData.kt:20-35`) — the
"wired to Gson for the backup JSON" clause of the goal, matching the stack at
`docs/DESIGN.md:86` and `docs/INSPIRATION.md:132`. The adapter exposes a
Gson-based serializer/deserializer for `BackupData` (a `TypeToken`-driven
`Gson` instance or a thin wrapper over it), so the data layer can hand the
backup payload to Gson for the on-disk JSON. The test file `BackupJsonTest.kt` in
`app/src/test/kotlin/com/piercingxx/txxt/data/` builds a `BackupData`, serializes
it with the Gson adapter, deserializes the JSON back, and asserts the round-trip
reproduces the messages/settings/blocklist/starred set exactly — a JVM test that
runs in the app module. This task owns the Gson backup wiring only; the Room
entities/DAOs/database are T1's and the pure mappers are T2, each with its own
verify so the verifies cannot be confused.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.data.BackupJsonTest
- files: app/src/main/kotlin/com/piercingxx/txxt/data/BackupJson.kt, app/src/test/kotlin/com/piercingxx/txxt/data/BackupJsonTest.kt

## Final gate

The whole-workstream gate is the repo's own test command covering both modules —
it compiles the `app/` module (including the `data/` layer and the Room schema
generation) and runs the full JVM suite (the WS2–WS5 core tests plus the new WS6
mapper and Gson-backup tests):

- ./gradlew :app:testDebugUnitTest :core:test --offline

It must exit 0 (83+ core tests plus the new `data/` JVM tests, 0 failures). No
individual task claims this command as its verify; T1's verify is the app-module
compile, T2's is the `MapperTest` node, T3's is the `BackupJsonTest` node, and
the Final gate confirms the whole suite holds together with the new `data/` layer
included. The on-device Room wiring and the schema's on-device correctness remain
deferred (see Deferred verification).