<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT WS1 — Project scaffold & build (task contract)

Scope: the task contract for **WS1 — Project scaffold & build** of the workstream
inventory at `contracts/TxxT.md:59`. It stands up the Android Gradle project for
`com.piercingxx.txxt` — Kotlin, Views + viewBinding, Room + Gson, minSdk 24,
target GrapheneOS Pixel 9 — with the manifest declaring no `INTERNET` permission,
the default-SMS-handler role, `POST_NOTIFICATIONS`, and `FLAG_SECURE`-ready
activities, plus the underlined-XX adaptive icon and the local toolchain wired
(the sibling-repo `local.properties` convention), so `./gradlew assembleDebug`
produces an installable APK.

Authoritative sources read this session: `docs/DESIGN.md`, `docs/INSPIRATION.md`,
`docs/PRIVACY.md`, `docs/FEATURES.md`, and the sibling-repo conventions measured
on disk (`Nope-Mode`, `xx-vitals` `local.properties` / `app/build.gradle` /
`AndroidManifest.xml`) and the brand source (`piercingxx-branding`).

## State of the tree (measured this session)

Measured on branch `main` (HEAD `03047a5`), clean working tree apart from the
untracked `contracts/`. The project is in the **design phase only** — there is no
Android project, no build system, no source code, and no test suite. Every
deliverable the WS1 goal names is **not started**; the goal is not stale.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| Android Gradle project for `com.piercingxx.txxt` | **Not started** | no `settings.gradle`, no `app/`, no `build.gradle*` anywhere in the tree (tree root holds only `docs/`, `contracts/`, `.gitignore`) |
| Kotlin, Views + viewBinding, Room + Gson | **Not started** | no source files, no `app/build.gradle` |
| `minSdk 24`, target GrapheneOS Pixel 9 | **Not started** | no build config exists; targetSdk 34 per the sibling convention (`Nope-Mode/app/build.gradle`) |
| Manifest: no `INTERNET`, default-SMS role, `POST_NOTIFICATIONS`, `FLAG_SECURE`-ready activities | **Not started** | no `AndroidManifest.xml` exists |
| Underlined-XX adaptive icon | **Not started** | no `res/` directory; the logomark **source** exists at `/media/Working-Storage/GitHub/piercingxx-branding/assets/logomark.svg` (two stacked white X's + underline on Ink, geometry documented) |
| Local toolchain wired (`local.properties` / SDK env var) | **Not started** | no `local.properties`; the Android SDK location env var is unset, `java` not on PATH |
| `./gradlew assembleDebug` → installable APK | **Not started** | no Gradle project to build |

**Gate report.** There is no runnable gate in this tree this session — no Gradle
project exists, so `./gradlew assembleDebug` cannot run and **no real numbers were
produced**. The measured state is **0 build files, 0 source files, 0 tests, 0
runnable gates**. The toolchain, however, is **present and runnable on this box**
(measured this session): `/home/piercingxx/.local/android-toolchain/` holds
`gradle-8.7`, `jdk17` (Temurin 17.0.20), and an SDK with platform `android-34`,
build-tools `34.0.0` (including `aapt2`), platform-tools, cmdline-tools, and
licenses. `gradle --version` ran successfully (Gradle 8.7 on JVM 17.0.20), so the
shell allowlist permits Java/Gradle processes. The Gradle module cache already
holds the exact dependency set WS1 needs — AGP `8.5.0`, Kotlin `1.9.24` (+kapt),
Room `2.6.1`, Gson `2.10.1` — and the `gradle-8.7-bin` wrapper distribution is
cached under `/home/piercingxx/.gradle/wrapper/dists/`. So once WS1 lands, this
box is expected to be able to run the Final gate and the aapt2 check; it just
cannot produce numbers now because there is nothing to build.

**Sibling-repo convention (measured this session, on disk):** both `Nope-Mode`
and `xx-vitals` wire the toolchain with `local.properties` containing exactly
`local.properties:1` = `sdk.dir=/home/piercingxx/.local/android-toolchain/sdk`.
`Nope-Mode` pins AGP `8.5.0`, Kotlin `1.9.24`, compileSdk 34, minSdk 24, targetSdk
34, Room `2.6.1`, Gson `2.10.1`, `viewBinding true`, and Room schema export via
kapt; its `gradle.properties` sets `android.useAndroidX=true`,
`org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m`, `org.gradle.caching=true`.
Its manifest carries the no-`INTERNET` comment pattern and `POST_NOTIFICATIONS`,
and its adaptive icon is the `mipmap-anydpi-v26/ic_launcher.xml` +
`drawable/ic_launcher_foreground.xml` + `values/ic_launcher_colors.xml` trio. WS1
mirrors these conventions.

## Deferred verification (the box cannot prove these)

- **The build gate's real numbers.** `./gradlew assembleDebug` produced no
  numbers this session because no project exists to build (measured: 0 build
  files). The toolchain and dependencies are present and runnable on this box, so
  the box is expected to run the Final gate once WS1 is implemented; the operator
  should confirm the shell allowlist still permits it at execution time. Until a
  real run happens, the "exits 0 and produces the APK" claim stays unverified.
- **The aapt2 no-`INTERNET` check on the built APK.** Needs the APK, which needs
  the build. T5's verify runs it against the APK produced by the Final gate; no
  APK exists this session to measure.
- **The adaptive icon *rendering*.** The box can verify the vector geometry
  matches the logomark source (T4), but whether the icon *renders* correctly on a
  GrapheneOS Pixel 9 launcher is a visual, on-device check that is the operator's.
- **Default-SMS-handler *eligibility* on-device.** WS1 declares the
  `android.permission.role.SMS` permission (making the app eligible); whether the
  OS actually offers the app as the default SMS handler is an on-device check.
  The SMS/MMS broadcast-receiver components that complete the role are WS7's
  scope (`contracts/TxxT.md:142`), not WS1's.

## Tasks

### T1 — Scaffold the Gradle project

Stand up the Gradle project root and the `app/` module, mirroring the sibling
convention measured this session: `settings.gradle` including `:app`, a top-level
`build.gradle` declaring AGP `8.5.0` and Kotlin `1.9.24` (`apply false`), a
`gradle.properties` setting `android.useAndroidX=true` (plus the Nope-Mode
jvmargs/caching lines), the Gradle 8.7 wrapper (`gradlew`, `gradlew.bat`,
`gradle/wrapper/gradle-wrapper.properties`), and `app/build.gradle` configured
for `namespace 'com.piercingxx.txxt'`, compileSdk 34, minSdk 24, targetSdk 34,
`viewBinding true`, Room `2.6.1` (+ kapt compiler, Room schema export), and Gson
`2.10.1`. The verify proves the skeleton is wired: the app module is included,
the plugins are declared, and the app module carries the required config. If any
of these files is missing or the config is wrong, the verify fails.

- verify: python3 scripts/verify_scaffold.py
- files: settings.gradle, build.gradle, gradle.properties, gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.properties, app/build.gradle, scripts/verify_scaffold.py

### T2 — Wire the local toolchain

Create `local.properties` with the measured sibling convention
(`sdk.dir=/home/piercingxx/.local/android-toolchain/sdk`) so Gradle finds the SDK
without relying on an SDK-location environment variable. The verify asserts
`local.properties` exists with the correct `sdk.dir` line and that the SDK
directory it points at actually exists on this box (measured present this
session).

- verify: python3 scripts/verify_toolchain.py
- files: local.properties, scripts/verify_toolchain.py

### T3 — Declare the manifest

Write `app/src/main/AndroidManifest.xml` declaring the app with **no `INTERNET`
permission** (the hard privacy constraint — the app must never be able to reach
the network), the default-SMS-handler role (`android.permission.role.SMS`), and
`POST_NOTIFICATIONS`; every `<activity>` is `FLAG_SECURE`-ready (the `FLAG_SECURE`
set in code, so the activity declares nothing that would block it — no
`android:screenOrientation` lock, no `android:exported` without intent-filter
need). The verify asserts the manifest exists, does **not** contain an `INTERNET`
permission, and declares the SMS role and `POST_NOTIFICATIONS`.

- verify: python3 scripts/verify_manifest.py
- files: app/src/main/AndroidManifest.xml, scripts/verify_manifest.py

### T4 — Add the underlined-XX adaptive icon

Create the adaptive-icon trio mirrored from the sibling convention: `res/mipmap-anydpi-v26/ic_launcher.xml`
and `ic_launcher_round.xml` referencing a foreground drawable and a color, plus
`res/drawable/ic_launcher_foreground.xml` and `res/values/ic_launcher_colors.xml`.
The foreground drawable renders the brand logomark (two stacked X's + underline)
as a vector, geometry faithful to the source at
`/media/Working-Storage/GitHub/piercingxx-branding/assets/logomark.svg`. The
verify asserts the four files exist and that the foreground drawable's vector
geometry encodes the logomark's two-X-plus-underline structure (checked by
parsing the `<path>` data for the expected stroke structure).

- verify: python3 scripts/verify_icon.py
- files: app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml, app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml, app/src/main/res/drawable/ic_launcher_foreground.xml, app/src/main/res/values/ic_launcher_colors.xml, scripts/verify_icon.py

### T5 — Build and prove the no-INTERNET APK

Run the build and prove the produced APK carries the privacy posture. This is the
task that produces the installable artifact the WS1 goal names. The verify runs
`./gradlew assembleDebug` (using the wired toolchain) and then inspects the
resulting APK with `aapt2 dump badging` to assert it does **not** declare the
`INTERNET` permission and does declare the SMS role and `POST_NOTIFICATIONS` —
proving the built artifact matches the manifest's privacy contract. This task
depends on T1–T3 being in place; it does not claim the Final gate.

- verify: python3 scripts/verify_apk.py
- files: scripts/verify_apk.py, app/build/outputs/apk/debug/app-debug.apk

## Final gate

The whole-workstream gate is the repo's own build command — it compiles the whole
project and produces the installable APK:

- ./gradlew assembleDebug

It must exit 0 and produce `app/build/outputs/apk/debug/app-debug.apk`. No
individual task claims this command as its verify; T5's verify runs it internally
as part of its own check and adds the aapt2 no-`INTERNET` assertion on top. The
aapt2 no-`INTERNET` result is deferred (see Deferred verification) until a real
build produces an APK to inspect.