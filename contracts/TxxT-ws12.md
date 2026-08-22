<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS12 — UI: settings (task contract)

Scope: the task contract for **WS12 — UI: settings** of the workstream inventory
at `contracts/TxxT.md:263`. It builds the settings screen in `ui/` with Views +
viewBinding — the seven theme presets, font toggle, backup/restore, blocking
management, lock-screen privacy options (sender-only / content / nothing,
defaulting to sender-only), notification preferences, the starred-contacts list,
and the theme auto-sync toggle (`docs/DESIGN.md:75`, `docs/PRIVACY.md:62`,
`docs/PRIVACY.md:141`).

`done when`: the screen renders on-device and every setting persists and takes
effect — theme preset applies, lock-screen privacy defaults to sender-only,
starred contacts are editable and exported/imported with backup, and a manual
in-app theme overrides auto-sync.

Authoritative sources read this session: `contracts/TxxT.md:263-274` (the WS12
goal and `done when`), `docs/DESIGN.md:75-80` (the Settings screen section —
theme presets, font toggle, backup/restore, blocking management, lock-screen
privacy, notification preferences, starred contacts, theme auto-sync),
`docs/PRIVACY.md:58-65` (§3 — lock-screen privacy options sender-only / content /
nothing, defaulting to **sender-only**), `docs/PRIVACY.md:105-121` (§6 — starred
contacts are first-class, persisted in Room, exported/imported with backup,
bypass every suppression), `docs/PRIVACY.md:125-142` (§7 — theme auto-sync with
the xx-launcher, local-only, and "a manual in-app theme still wins over auto-sync"),
`docs/FEATURES.md:58-59` (the seven named presets — AMOLED Night, Graphite,
Forest Night, Ocean Drift, Burgundy, Paper, Mist), `docs/FEATURES.md:117-118`
(font — Space Mono for chrome + JetBrains Mono for body, shipped in `res/font/`),
and the existing source measured on disk (`ui/ThreadActivity.kt`,
`ui/ThreadAdapter.kt`, `core/Blocklist.kt`, `core/StarredBypass.kt`,
`core/BackupData.kt`, `core/BackupSerializer.kt`,
`service/NotificationPosture.kt`, `service/NotificationPolicy.kt`,
`app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/com/piercingxx/txxt/MainActivity.kt`).

## State of the tree (measured this session)

Measured on branch `laundry-bot/queue-TxxT-ws11-i6` (HEAD `c3b979f`), working
tree clean apart from the untracked `contracts/TxxT-ws11-corrective.md`. The
`ui/` package exists at
`app/src/main/kotlin/com/piercingxx/txxt/ui/` and carries the WS11 thread screen
(`ThreadActivity.kt`, `ThreadAdapter.kt`, `ThreadMessagePresenter.kt`). The
**settings screen is not started** — there is no `SettingsActivity`, no
`Settings*` class in `ui/`, no `res/font/` directory, no SharedPreferences usage,
and no theme code anywhere in the tree. Every deliverable the WS12 goal names is
**not started**; the goal is not stale.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| The settings screen in `ui/` (Views + viewBinding) | **Not started** | no `SettingsActivity` or `Settings*` class anywhere (measured: `search_text` for `SettingsActivity` returns only `docs/INSPIRATION.md` prose; `find app/src -type f` lists no settings source) |
| The seven theme presets | **Not started** | no theme code, no preset enum, no `res/font/` (measured: `find app/src/main/res -type d` returns only `drawable`, `layout`, `mipmap-anydpi-v26`, `values`; `search_text` for `theme|Theme|prefs|Preference|SharedPreferences` returns 0 matches across the tree) |
| Font toggle | **Not started** | no font-mode setting, no `res/font/` directory (measured above) |
| Backup/restore | **Not started** | the pure `core/BackupSerializer.kt` + `core/BackupData.kt` already serialize messages/settings/blocklist/starred (WS5), but no settings-screen backup/restore wiring exists |
| Blocking management | **Not started** | the pure `core/Blocklist.kt` already models keyword/phrase rules (WS9), but no settings-screen UI or store wiring exists |
| Lock-screen privacy options (sender-only / content / nothing, defaulting to sender-only) | **Not started** | no lock-screen setting exists; `docs/PRIVACY.md:61-62` is the authority (default **sender-only**) |
| Notification preferences | **Not started** | `service/NotificationPosture.kt` + `service/NotificationPolicy.kt` already model the global posture and per-contact overrides (WS8), but no settings-screen exposure exists |
| The starred-contacts list | **Not started** | the pure `core/StarredBypass.kt` already models starred contacts (WS8), but no editable settings-screen list exists |
| Theme auto-sync toggle | **Not started** | no auto-sync setting exists; `docs/PRIVACY.md:141` is the authority (manual in-app theme wins over auto-sync) |

**Gate report (run this session, real numbers).** `./gradlew
:app:testDebugUnitTest --offline --rerun-tasks` (with `JAVA_HOME` and
`ANDROID_HOME` already set on this box — measured at
`/home/piercingxx/.local/android-toolchain/`) → **BUILD SUCCESSFUL in 11s**, 34
actionable tasks executed. Test report
(`app/build/test-results/testDebugUnitTest/*.xml`, this session): **19 suites,
121 tests, 0 failures, 0 errors**. The existing `ui/` suites are green:
`ThreadLayoutTest` 4, `ThreadMessagePresenterTest` 4, `ThreadWiringTest` 6. The
WS12 settings suites will be added to this same `:app:testDebugUnitTest` task.

**Existing building blocks WS12 consumes (measured this session, on disk):**
- `core/Blocklist.kt` — `addKeyword`/`addPhrase`/`removeKeyword`/`removePhrase`/
  `isBlocked`/`keywords()`/`phrases()` (WS9).
- `core/StarredBypass.kt` — `isStarred`/`bypasses`/`reason` over a set of
  addresses (WS8).
- `core/BackupData.kt` + `core/BackupSerializer.kt` — the local-JSON backup with
  `version`, `messages`, `settings` (a `Map<String,String>`), `blocklist`, and
  `starred` sections; `serialize`/`deserialize` with version and range gates
  (WS5).
- `service/NotificationPosture.kt` — `Posture` enum (`NOTIFY`/`REDACTED`/
  `SUPPRESS`), `Override` enum, `decide` (WS8).
- `ui/ThreadActivity.kt` — the established Activity pattern (Views, FLAG_SECURE
  set in code, `TxxTDatabase.build`, `MainScope`), which WS12's `SettingsActivity`
  mirrors.
- `app/src/main/AndroidManifest.xml` — the manifest that must register
  `SettingsActivity`; `MainActivity.kt` is the launcher that must reach it.

## Deferred verification (the box cannot prove these)

- **The screen *rendering* on-device.** `done when` requires "the screen renders
  on-device". The box has no device; it verifies the activity compiles, the
  manifest registers it, the class resolves, and the layout is wired
  (source-reading + Class.forName, mirroring the WS11 `ThreadWiringTest`
  pattern). Whether the screen *renders* correctly on a GrapheneOS Pixel 9 is the
  operator's on-device check.
- **Every setting *persisting* to disk across a process restart.** The box
  verifies behaviorally (T1–T4) that the pure `SettingsStore` holds the settings
  with the correct defaults and round-trips through the backup format, and
  structurally (T5) that `SettingsActivity` writes the store through
  `SharedPreferences`. Whether a real on-device process restart retains the
  settings is an on-device check.
- **The theme preset *applying*.** WS12 exposes and persists the chosen preset
  (T2). The actual rendering of the seven presets and the theme auto-sync
  receiver are **WS14's scope** (`contracts/TxxT.md:285-297`) — WS12 builds the
  settings screen and the persisted selection, not the theme engine. Whether the
  preset visibly re-themes the app on-device is WS14's on-device check.
- **The font *rendering*.** WS12 persists the font-mode toggle (T2); the actual
  font resource files (`res/font/space_mono.ttf`, `res/font/jetbrains_mono.ttf`)
  are not in the tree (measured: no `res/font/` directory) and ship with the
  theme system (WS14). Whether the chosen font visibly renders is an on-device
  check.
- **The auto-sync *following* the launcher.** WS12 exposes and persists the
  auto-sync toggle (T1). The launcher's actual theme-publish mechanism is a spec,
  not a measured fact (`docs/PRIVACY.md:133-140`), and the sync receiver is
  WS14's scope. WS12 verifies the toggle persists and that a manual in-app theme
  overrides auto-sync (T1) — the launcher-side integration is deferred to WS14.
- **Backup/restore *to a real file on device*.** The box proves behaviorally
  (T4) that `SettingsStore` round-trips through `BackupSerializer`'s JSON
  format. Writing/reading an actual file on device through the screen is an
  on-device check.

## Tasks

### T1 — Settings store model: defaults, lock-screen privacy, notification posture, auto-sync

Build the pure, JVM-testable settings model in `ui/` — the persistence-shaped
seam the settings screen (T5) drives, mirroring the WS11-corrective
`ThreadMessageLoader` seam pattern and the WS7 pure-policy pattern. Add
`app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsStore.kt`: a class holding
every setting the goal names, with the correct defaults:
- **Lock-screen privacy** — an enum `LockScreenPrivacy { SENDER_ONLY, CONTENT,
  NOTHING }` defaulting to **SENDER_ONLY** (`docs/PRIVACY.md:62`).
- **Notification posture** — the global posture (reusing the existing
  `service.NotificationPosture.Posture` values NOTIFY/REDACTED/SUPPRESS),
  defaulting to REDACTED (sender-name-only, `docs/PRIVACY.md:60-61`).
- **Theme auto-sync toggle** — a `Boolean` defaulting to **true** (auto-sync is
  the designed default, `docs/PRIVACY.md:129`), with the rule that a manual
  in-app theme overrides auto-sync (`docs/PRIVACY.md:141`).
- **Theme preset** — the chosen preset (T2's `ThemePreset`), defaulting to
  AMOLED_NIGHT.
- **Font mode** — the font toggle (T2's `FontMode`), defaulting to the
  monospace-body mode.
- **Blocking rules** and **starred contacts** — the editable lists (T3),
  defaulting empty.

The store exposes get/set for each setting and a `manualThemeOverride()` path
that records a manual selection as winning over auto-sync. Zero `android.*`
imports so the model is JVM-testable without a device.

Create `app/src/test/kotlin/com/piercingxx/txxt/ui/SettingsStoreTest.kt` — a JVM
unit test that calls `SettingsStore` **by name** and asserts behaviour: (a) a
fresh store defaults lock-screen privacy to SENDER_ONLY, notification posture to
REDACTED, auto-sync to true, theme to AMOLED_NIGHT, and empty blocklist/starred;
(b) setting lock-screen privacy to CONTENT or NOTHING is read back; (c) a manual
in-app theme selection wins over auto-sync (the store's override rule). This
test would fail before the fix because `SettingsStore` does not exist (compile
error).

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.ui.SettingsStoreTest
- files: app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsStore.kt, app/src/test/kotlin/com/piercingxx/txxt/ui/SettingsStoreTest.kt

### T2 — Theme presets and the font toggle

Build the two named-choice models the settings screen exposes, as pure Kotlin in
`ui/` with zero `android.*` imports. Add
`app/src/main/kotlin/com/piercingxx/txxt/ui/ThemePreset.kt`: an enum of the
**seven named presets** — AMOLED_NIGHT, GRAPHITE, FOREST_NIGHT, OCEAN_DRIFT,
BURGUNDY, PAPER, MIST — exactly the brand-guide §3.3 set
(`docs/FEATURES.md:58-59`, `docs/PRIVACY.md:130-131`), with a display name for
each. Add `app/src/main/kotlin/com/piercingxx/txxt/ui/FontMode.kt`: an enum for
the font toggle — the monospace-body mode (Space Mono chrome + JetBrains Mono
body, `docs/FEATURES.md:117`) and a proportional-body mode — with a display name.
`SettingsStore` (T1) stores the chosen `ThemePreset` and `FontMode` and exposes
get/set for each.

Create `app/src/test/kotlin/com/piercingxx/txxt/ui/ThemePresetTest.kt` — a JVM
unit test that calls `ThemePreset` and `FontMode` **by name** and asserts
behaviour: (a) `ThemePreset` has exactly the seven named values (AMOLED_NIGHT,
GRAPHITE, FOREST_NIGHT, OCEAN_DRIFT, BURGUNDY, PAPER, MIST) and each carries a
non-blank display name; (b) `FontMode` has the monospace-body and proportional
modes; (c) a `SettingsStore` can set and read back a chosen `ThemePreset` and
`FontMode`. This test would fail before the fix because `ThemePreset` and
`FontMode` do not exist (compile error).

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.ui.ThemePresetTest
- files: app/src/main/kotlin/com/piercingxx/txxt/ui/ThemePreset.kt, app/src/main/kotlin/com/piercingxx/txxt/ui/FontMode.kt, app/src/test/kotlin/com/piercingxx/txxt/ui/ThemePresetTest.kt

### T3 — Blocking management and the starred-contacts list

Wire the settings screen's blocking and starred slices to the existing pure core
models, as pure Kotlin in `ui/` with zero `android.*` imports. Add
`app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsBlocking.kt`: a thin seam
over `core.Blocklist` (add/remove keyword and phrase rules, list current rules,
answer `isBlocked`) so the settings screen's blocking-management section has a
JVM-testable surface. Add
`app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsStarred.kt`: a thin seam over
`core.StarredBypass` (add/remove a starred address, list the starred set, answer
`isStarred`/`bypasses`) so the settings screen's starred-contacts list — editable
and exported/imported with backup (`docs/PRIVACY.md:111`) — has a JVM-testable
surface. `SettingsStore` (T1) holds the blocklist and starred lists and exposes
add/remove through these seams.

Create `app/src/test/kotlin/com/piercingxx/txxt/ui/SettingsBlockingStarredTest.kt`
— a JVM unit test that calls `SettingsBlocking` and `SettingsStarred` **by
name** and asserts behaviour: (a) adding a keyword and a phrase rule makes
`isBlocked` true for matching terms and removing them makes it false; (b) adding
a starred address makes `isStarred`/`bypasses` true and removing it makes them
false; (c) a `SettingsStore` round-trips its blocklist and starred lists through
these seams. This test would fail before the fix because `SettingsBlocking` and
`SettingsStarred` do not exist (compile error).

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.ui.SettingsBlockingStarredTest
- files: app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsBlocking.kt, app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsStarred.kt, app/src/test/kotlin/com/piercingxx/txxt/ui/SettingsBlockingStarredTest.kt

### T4 — Backup/restore integration for the settings store

Wire the settings store to the existing pure backup format so every setting —
including the starred-contacts list and the blocklist — is exported and imported
with backup (`docs/PRIVACY.md:111`). Add
`app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsBackup.kt`: a pure seam over
`core.BackupSerializer` + `core.BackupData` that (a) serializes a `SettingsStore`
into a `BackupData` (its `settings` map carrying the theme, font, lock-screen
privacy, notification posture, and auto-sync values; its `blocklist` and
`starred` lists carrying the store's lists) and (b) restores a `SettingsStore`
from a `BackupData`, applying the persisted values. Zero `android.*` imports.

Create `app/src/test/kotlin/com/piercingxx/txxt/ui/SettingsBackupTest.kt` — a JVM
unit test that calls `SettingsBackup` **by name** and asserts behaviour: (a) a
store with a non-default theme, font, lock-screen privacy, notification posture,
auto-sync, blocklist, and starred list serializes into a `BackupData` whose
`settings`/`blocklist`/`starred` carry those values; (b) restoring that
`BackupData` into a fresh store reproduces the original settings (round-trip);
(c) the round-trip also holds through `BackupSerializer.serialize` →
`deserialize` (the JSON text), so the settings survive the app's own backup
format. This test would fail before the fix because `SettingsBackup` does not
exist (compile error).

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.ui.SettingsBackupTest
- files: app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsBackup.kt, app/src/test/kotlin/com/piercingxx/txxt/ui/SettingsBackupTest.kt

### T5 — SettingsActivity, layout, manifest registration, and launch wiring

Build the settings screen itself. Add
`app/src/main/res/layout/activity_settings.xml` — a Views layout on AMOLED black
(`#000000`) with sections for the seven theme presets, the font toggle, backup/
restore, blocking management, lock-screen privacy (sender-only / content /
nothing), notification preferences, the starred-contacts list, and the theme
auto-sync toggle, following the text-first, one-accent design
(`docs/DESIGN.md:75-80`). Add
`app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsActivity.kt` — an `Activity`
using viewBinding that loads a `SettingsStore` (T1), renders each control from
it, writes each change back through the store (persisting via
`SharedPreferences` so a change survives restart), and wires the T3 blocking/
starred seams and the T4 backup/restore seam. Register `SettingsActivity` in
`app/src/main/AndroidManifest.xml` (not exported, FLAG_SECURE set in code
mirroring `ThreadActivity`, `docs/PRIVACY.md:160-161`) and reach it from
`MainActivity.kt` (a launcher entry to the settings screen).

Create `app/src/test/kotlin/com/piercingxx/txxt/ui/SettingsWiringTest.kt` — a JVM
unit test, mirroring the WS11 `ThreadWiringTest` manifest/source-reading pattern:
(a) `Class.forName("com.piercingxx.txxt.ui.SettingsActivity")` resolves; (b) the
manifest text contains `.ui.SettingsActivity`; (c) reading `SettingsActivity.kt`
source confirms it uses viewBinding, loads `SettingsStore`, writes through
`SharedPreferences`, and sets FLAG_SECURE; (d) reading `MainActivity.kt` source
confirms it reaches the settings screen. This test would fail before the fix
because `SettingsActivity` does not exist (Class.forName throws) and the manifest
does not declare it.

- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.txxt.ui.SettingsWiringTest
- files: app/src/main/kotlin/com/piercingxx/txxt/ui/SettingsActivity.kt, app/src/main/res/layout/activity_settings.xml, app/src/main/AndroidManifest.xml, app/src/main/kotlin/com/piercingxx/txxt/MainActivity.kt, app/src/test/kotlin/com/piercingxx/txxt/ui/SettingsWiringTest.kt

## Final gate

The whole-workstream gate is the app module unit test suite — it must pass with
the new settings suites included:

- ./gradlew :app:testDebugUnitTest --offline

It must exit 0. No individual task claims this command as its verify; each
task's verify is its own test node (`SettingsStoreTest`, `ThemePresetTest`,
`SettingsBlockingStarredTest`, `SettingsBackupTest`, `SettingsWiringTest`), and
the Final gate confirms the whole suite (121 existing tests plus the new setting
suites) holds together with the settings screen wired. The on-device checks (the
screen *rendering*, settings *persisting* across a restart, the theme preset
*applying*, the font *rendering*, backup/restore to a real file, and the
launcher auto-sync) remain deferred (see Deferred verification) because the box
has no device, and the theme engine and launcher integration are WS14's scope.