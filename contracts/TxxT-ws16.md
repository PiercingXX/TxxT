<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS16 — Packaging & privacy verification (task contract)

Scope: the task contract for **WS16 — Packaging & privacy verification** of the
workstream inventory at `contracts/TxxT.md:313`. It produces the final sideloaded
APK and verifies the privacy claims: no `INTERNET` permission (machine-checkable
via `aapt2 dump permissions`, `docs/INSPIRATION.md:117`), no analytics/crash
reporting, and a README in the dry maker register stating the privacy posture as
fact (`docs/INSPIRATION.md:86`).

Authoritative sources read this session: `docs/INSPIRATION.md:117` (the
no-`INTERNET` machine-checkable claim), `docs/INSPIRATION.md:86` (the dry maker
register voice), `docs/PRIVACY.md:158-159` ("No analytics / no crash reporting.
Covered by the no-`INTERNET` claim; state it as fact, not aspiration"), and the
sibling-repo build conventions measured on disk.

## State of the tree (measured this session)

Measured on branch `main` (HEAD `e9d7ee2`), clean working tree apart from the
untracked `contracts/TxxT-ws15.md`. The project is fully built and the privacy
posture already holds — **four of the five deliverables the goal names are
already done and machine-verified this session**. Only the README is not
started. The goal is substantially stale; the gap is the README.

**Gate report (run this session).** `./gradlew assembleDebug testDebugUnitTest`
exits 0 (`BUILD SUCCESSFUL in 3s`). The app-module test results report **171
tests, 0 failures, 0 errors** across 26 test classes (measured from
`app/build/test-results/testDebugUnitTest/*.xml`). The APK is produced at
`app/build/outputs/apk/debug/app-debug.apk` (6,011,299 bytes).

**aapt2 no-`INTERNET` check (run this session).**
`aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk` lists only:
`role.SMS`, `POST_NOTIFICATIONS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `SEND_SMS`,
`READ_SMS`, `WRITE_SMS`, and the auto-generated
`com.piercingxx.txxt.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. **No `INTERNET`
permission is declared** — the machine-checkable privacy claim already holds on
the built APK.

**No-analytics/crash-reporting check (run this session).** A regex grep
(`firebase|crashlytics|analytics|telemetry|sentry|mixpanel|amplitude`) over
`app/src` and `core/src` returns **0 matches**; `app/build.gradle` and
`core/build.gradle` declare no analytics/crash-reporting dependency (only
appcompat, constraintlayout, material, Room, Gson, coroutines, junit, mockk,
espresso). `docs/PRIVACY.md:158-159` already states the posture as fact.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| Final sideloaded APK produced | **Already done** | `app/build/outputs/apk/debug/app-debug.apk` exists (6,011,299 bytes); `./gradlew assembleDebug testDebugUnitTest` exits 0 |
| Build-and-unit-test gate exits 0 on a clean checkout | **Already done** | `BUILD SUCCESSFUL in 3s`; **171 tests, 0 failures, 0 errors** across 26 classes |
| No `INTERNET` permission (aapt2) | **Already done** | `aapt2 dump permissions` on the built APK shows no `INTERNET`; manifest declares none (`app/src/main/AndroidManifest.xml:24-25`) |
| No analytics / crash reporting | **Already done** | grep of `app/src` + `core/src` = 0 matches; no such deps in `app/build.gradle` / `core/build.gradle` |
| README in the dry maker register stating the privacy posture as fact | **Not started** | no `README.md` at the repo root (searched: no `README*` file, no `renders/` directory) |

**Deferred verification (the box cannot prove these).** Whether the APK
*installs and runs* as the default SMS handler on a GrapheneOS Pixel 9 is an
on-device check that is the operator's — the box proves the artifact builds, the
unit tests pass, and the machine-checkable privacy claims hold. Whether the
README *reads* as dry-and-opinionated to a human is a judgment; the box verifies
it states the posture as fact and carries the maker-register markers. Nothing
here touches the network; the no-`INTERNET` claim is preserved, not re-opened.

## Tasks

### T1 — Write the README in the dry maker register stating the privacy posture as fact

No `README.md` exists at the repo root (measured this session). Create
`README.md` in the dry maker register voice (`docs/INSPIRATION.md:86` — dry,
opinionated, "Defaults with a spine", no throat-clearing) that states the privacy
posture **as fact, not aspiration** (`docs/PRIVACY.md:158-159`): the app ships
with **no `INTERNET` permission** (fully offline / local-first), **no
analytics**, and **no crash reporting** — each stated as a plain factual claim
the build already backs. Also create `scripts/verify_readme_privacy.py`, which
asserts the README exists and that its text carries the posture: it must mention
`INTERNET` (as absent), `analytics`, `crash`, and a local/offline claim. The
verify fails if the README is missing or omits any of those factual posture
statements.

- verify: python3 scripts/verify_readme_privacy.py
- files: README.md, scripts/verify_readme_privacy.py

### T2 — Verify the privacy claims on the built APK

The goal names "verify the privacy claims" as a deliverable; the claims already
hold (measured this session) but no contract task machine-checks them
repeatably. Create `scripts/verify_privacy_claims.py`, which builds the APK
(`./gradlew assembleDebug`), runs `aapt2 dump permissions` on it and asserts it
does **not** declare `android.permission.INTERNET`, and greps `app/src`,
`core/src`, `app/build.gradle`, and `core/build.gradle` for
analytics/crash-reporting markers (`firebase`, `crashlytics`, `analytics`,
`telemetry`, `sentry`, `mixpanel`, `amplitude`) asserting **zero** matches. The
script locates `aapt2` via `ANDROID_HOME` (set this session to
`/home/piercingxx/.local/android-toolchain/sdk`, build-tools `34.0.0`). The
verify fails if the APK declares `INTERNET` or any analytics/crash-reporting
marker appears in source or build files — so a future regression that adds
`INTERNET` or telemetry is caught, not just this session's clean state.

- verify: python3 scripts/verify_privacy_claims.py
- files: scripts/verify_privacy_claims.py

## Final gate

The whole-workstream gate is the repo's own build-and-test command, which
compiles the whole project, runs the full unit suite, and produces the
installable APK:

- verify: ./gradlew assembleDebug testDebugUnitTest

It must exit 0 and produce `app/build/outputs/apk/debug/app-debug.apk`. No
individual task claims this command as its verify; T2's verify runs
`assembleDebug` internally as part of its own check and adds the aapt2
no-`INTERNET` and no-analytics assertions on top, and T1's verify checks the
README. This gate closes the workstream by proving the build-and-test suite
holds together.