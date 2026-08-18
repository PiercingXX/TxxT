# TxxT — Compiled Feature List & Questions

## Compiled feature list

### Core messaging
- SMS + MMS
- Group MMS
- Attachments (any file type)
- Voice messages
- Emoji reactions
- Message pinning
- Message sorting
- Archiving
- Delayed sending
- Scheduled messages
- Quick reply from notifications
- Swipe actions

### Privacy / blocking
- Robust blocking (unknown senders + keyword/phrase filters)
- Blocklist export/import
- Lock-screen privacy options (sender only / content / nothing)
- Fully offline — no Internet permission

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
- Theme presets (six, like the launcher)
- Text-first surfaces

## Questions for the operator

### Design
1. **Aesthetic:** hybrid (defaulted) — AMOLED black + Space Mono + gestures,
   but with compose bar/bubbles. Or full text-first terminal, or conventional
   material? *(Defaulted to hybrid; confirm or override.)*
2. **Theme presets:** replicate the launcher's six presets, or a single AMOLED
   black? What are the six?
3. **Font:** Space Mono for everything, or Space Mono for chrome + a
   proportional font for message body?
4. **Icon grid:** strictly no icons, or minimal glyphs where unavoidable?

### Messaging scope
5. **RCS:** include or skip? (GrapheneOS + Google-free usually means skip
   RCS/Jibe — recommend skip.)
6. **Encryption:** plain SMS/MMS, Silence-style SMS encryption (Signal
   protocol, offline), or Signal-protocol integration?
7. **Scheduled messages:** include?
8. **Voice messages:** include?
9. **Emoji reactions:** include? (SMS has no native reactions — these are
   locally-rendered, like QKSMS.)

### Backup / data
10. **Backup format:** JSON (matches launcher) vs standard SMS XML export?
11. **Backup scope:** messages only, or settings/blocklist too?

### Privacy / permissions
12. **Internet permission:** confirm zero (fully offline)? Any feature that
    needs network (e.g. contact avatars from a service)?
13. **Lock-screen privacy:** sender-only / content / nothing — which default?

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
19. **Any features to explicitly EXCLUDE** (e.g. no voice messages, no
    scheduled send, no MMS)?

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
- **Q12 internet:** confirm zero — no `INTERNET` permission, matching Nope-Mode.
  It is a machine-checkable privacy claim.
- **Q14 language:** Kotlin confirmed. **Stack:** Views + viewBinding (not
  Compose), Room + Gson, pure-Kotlin core — all from the sibling repos.
- **Q15 package:** `com.piercingxx.txxt` per the brand naming system.
