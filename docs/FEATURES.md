# TxxT — Compiled Feature List & Questions

## Compiled feature list

### Core messaging
- SMS + MMS
- Group MMS
- Attachments — **photos ship**: the compose bar's `⊕` opens the Android photo
  picker (`PickVisualMedia`, ImageOnly), which needs **no storage permission**;
  the picked photo is staged app-private, shown as a one-line indicator that can
  be removed without sending, and dispatched through the MMS pipeline, where the
  metadata scrub happens. A caption typed alongside a photo is sent as its own
  SMS — the MMS entry point carries media only, and a dropped caption would
  misreport what was sent. Other file types are **not** wired yet.
  - **Why staged, not referenced:** the picker's grant is one-shot and not
    persistable, so the bytes are copied into app-private cache the moment you
    pick. The send cannot fail on an expired grant however long you spend
    typing. The copy is deleted when the photo is sent or removed, and copies
    orphaned by a killed process are swept after a day.
  - The send goes through `SendPipeline.sendMms`, which is where EXIF/XMP/IPTC
    stripping happens (PRIVACY.md §4).
- Emoji reactions
- Message pinning
- Message sorting
- Archiving
- Delayed sending
- Scheduled messages
- Quick reply from notifications
- Swipe actions
- **Excluded:** voice messages (never sent or received — PRIVACY.md §5)

### Privacy / blocking
- Robust blocking (unknown senders + keyword/phrase filters)
- Blocklist export/import
- Lock-screen privacy options (sender only / content / nothing)
- No analytics, no crash reporting, no ads, no Play Services
- Read/typing receipts, delivery reports, MMS smart features **off by default**
- RCS **excluded** (the vector for receipts + typing indicators)
- **No message bubbles in the thread UI** (text-first lines) and **no
  notification bubbles / chat-heads** — ever
- **Metadata scrubbed** from every image/video send (EXIF/XMP/IPTC/video atoms)
- **Starred contacts** — call-through that bypasses every suppression
- Notification content redacted by default (sender name only)
- MMS auto-download off (fetch on explicit tap)
- Full posture: `PRIVACY.md`

### Backup
- SMS export/import
- JSON backup (matching launcher pattern)

### Accessibility
- In-app speech-to-text
- In-app text-to-speech

### Design (brand)
- AMOLED black
- Space Mono
- Gesture-driven
- Minimal / no icon grid
- Theme presets (seven, like the launcher)
- Text-first surfaces

## Questions for the operator

### Design
1. **Aesthetic:** hybrid (defaulted) — AMOLED black + Space Mono + gestures,
   with compose bar, **text-first message lines (no bubbles)**. Or full
   text-first terminal, or conventional material? *(Defaulted to hybrid;
   confirm or override.)*
2. **Theme presets:** replicate the launcher's presets, or a single AMOLED
   black? **Resolved:** the brand guide §3.3 names seven — AMOLED Night,
   Graphite, Forest Night, Ocean Drift, Burgundy, Paper, Mist.
3. **Font:** Space Mono for everything, or Space Mono for chrome + a
   proportional font for message body?
4. **Icon grid:** strictly no icons, or minimal glyphs where unavoidable?

### Messaging scope
5. **RCS:** include or skip? (GrapheneOS + Google-free usually means skip
   RCS/Jibe — recommend skip.) **Resolved: skip/exclude** — RCS is the vector
   for read receipts and typing indicators, which are off by default
   (PRIVACY.md §1).
6. **Encryption:** plain SMS/MMS, Silence-style SMS encryption (Signal
   protocol, offline), or Signal-protocol integration?
7. **Scheduled messages:** include?
8. **Voice messages:** include? **Resolved: excluded** — never sent, never
   received; inbound audio MMS is dropped and the sender is optionally told via
   an auto-reply SMS (PRIVACY.md §5).
9. **Emoji reactions:** include? (SMS has no native reactions — these are
   locally-rendered, like QKSMS.)

### Backup / data
10. **Backup format:** JSON (matches launcher) vs standard SMS XML export?
11. **Backup scope:** messages only, or settings/blocklist too?

### Privacy / permissions
12. **Network-touching features:** any feature that would reach a service of
    its own (e.g. contact avatars fetched from a directory)?
13. **Lock-screen privacy:** sender-only / content / nothing — which default?
    **Resolved: sender-only** — notification content is redacted by default
    (PRIVACY.md §3).

### Stack / packaging
14. **Language:** Kotlin (matches launcher) — confirm.
15. **Package name / app name:** "TxxT" display name? Package id (e.g.
    `xx.txxt`)?
16. **Min Android version:** Pixel 9 ships Android 15; target that or lower?
17. **Sideload:** confirm APK-only, no Play Store listing.

### Gboard / IME
18. **Gboard:** confirm it must work with Gboard (it will — standard text
    field). Any IME to prioritize or explicitly support?

### Exclusions
19. **Any features to explicitly EXCLUDE**? **Resolved:** voice messages (never
    sent/received), RCS, **message bubbles in the thread UI**, notification
    bubbles/chat-heads, MMS auto-download, read/typing receipts — all excluded
    or off by default (PRIVACY.md §1, §2, §5).

## Design answers now resolved (from reading the private repos)

The three PiercingXX repos were read this session and their findings are in
`INSPIRATION.md`. They resolve the design questions above:

- **Q1 aesthetic:** hybrid confirmed as the right call — the brand guide's
  "X's layout, PiercingXX's skin" approach (xx-vitals D5) is the
  legally-cleanest and brand-correct answer.
- **Q2 theme presets:** the brand guide §3.3 ships the named background presets
  (AMOLED Night, Graphite, Forest Night, Ocean Drift, Burgundy, Paper, Mist) —
  reuse those names/values rather than inventing new ones.
- **Q3 font:** Space Mono (chrome) + JetBrains Mono (body), both shipped in
  `res/font/`. Monospace is the identity.
- **Q4 icon grid:** minimal glyphs only; the underlined-XX logomark on an Ink
  tile for the app icon.
- **Q12 network-touching features:** none. Contact names come from the local
  `ContactsContract` provider (PRIVACY.md §10), never from a directory service;
  avatars are local monograms or nothing (PRIVACY.md §8.3).
- **Q14 language:** Kotlin confirmed. **Stack:** Views + viewBinding (not
  Compose), Room + Gson, pure-Kotlin core — all from the sibling repos.
- **Q15 package:** `com.piercingxx.txxt` per the brand naming system.
- **Q5/Q8/Q13/Q19 privacy:** resolved in `PRIVACY.md` — receipts/typing off,
  RCS excluded, voice messages excluded, lock-screen sender-only, **no message
  bubbles in the thread UI**, no notification bubbles, metadata scrubbed,
  starred-contacts call-through, theme auto-sync with the xx-launcher.

## Blocked items (need operator action)
- **Nagatha cleanroom** of the best options — explicitly deferred by operator.
- **Theme-sync channel** — the xx-launcher's actual theme-publish mechanism
  needs its source to confirm; the broadcast contract in PRIVACY.md §7 is a
  proposal until then.
