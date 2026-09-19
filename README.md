# TxxT

> An SMS client whose defaults are the product.

Holds the system SMS role on GrapheneOS. Text-first. The features are ordinary;
what makes it worth building is which of them ship switched off.

Photos go as MMS — scrubbed on send, fetched on arrival, shown as `[Photo]`
until tapped. Compose is JetBrains Mono. No colour emoji.

No analytics. No crash reporting. No Play Services. No link preview. No
network contact enrichment. TxxT contacts no service of its own. Messages
travel over the carrier, because that is what sending a text is.
`scripts/verify_privacy_claims.py` dumps the APK permissions and fails on
drift either way.

Read receipts, typing indicators, delivery reports, MMS auto-download, and
notification preview are **off by default**. RCS is out. Metadata is scrubbed
on every send — not optional. No voice messages. No mic.

Contact names come from the system address book. Decline `READ_CONTACTS` and
you get numbers, never blank rows. Backup is a local JSON export.

**Installed. SMS role held. Not proven against a live SIM.** Treat send and
receive as untested until a real message makes the round trip.

```
package: com.piercingxx.txxt    minSdk 24
```

## Build

```sh
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug :core:test :app:testDebugUnitTest
./gradlew :app:installDebug
```

`installDebug` disables device RCS so Chat senders fall back to MMS. Voice IMS
is left alone.

`core/` has zero `android.*` imports. `scripts/verify_no_android_imports.py`
enforces that.

- `docs/PRIVACY.md` — privacy & notifications
- `docs/DESIGN.md` — architecture
- `docs/FEATURES.md` — the list
