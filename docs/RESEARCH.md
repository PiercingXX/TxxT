# TxxT — Open-Source SMS App Research

Research phase for a Google-free, sideloaded SMS/MMS app for a GrapheneOS Pixel 9.
Scope: research, question, and design phases only. No build.

## Design DNA (from operator's PiercingXX brand)

Source: operator's GitHub profile (PiercingXX) and stated brand repos
(`piercingxx-brand`, `nope-mode`, `xx-vitals` — private, to be read once granted).

- **Simple, clean UI** — reproducible, text-first, no decoration.
- **Text-first surfaces** — no icon grids; type and whitespace carry the UI.
- **Gesture-driven** — minimize taps; swipes and shortcuts do the work.
- **AMOLED black** — true black background for an OLED panel.
- **Space Mono font** — monospace, technical, uniform.
- **Theme presets** — the launcher ships six presets; replicate the pattern.
- **JSON backup** — the launcher uses JSON; carry that over.
- **Local-first / offline** — everything runs locally, no cloud dependency.

## Open-source SMS apps researched

### QUIK / QKSMS (moezbhatti/QKSMS → QUIK)
GPLv3, ~2.7k stars. "The most beautiful SMS messenger for Android."
- Scheduled messages
- Message backup
- In-app speech-to-text and text-to-speech
- Blocking + archiving
- Voice messages
- Any-file attachments
- Message sorting, pinning, delayed sending
- Quick reply from notifications
- Swipe actions
- Emoji reactions

### Fossify Messages (fork of Simple Messages)
GPLv3, ~1.5k stars. Clean, no-nonsense, fully offline.
- SMS + MMS group messaging
- Robust blocking: unknown contacts, keyword/phrase filters, blocklist export/import
- SMS backup export/import
- Lock-screen privacy (sender only / content / nothing)
- Fast search
- **No Internet permission at all** (fully offline)
- Material design + dark theme, no ads, no unnecessary permissions

### Silence (SilenceIM/Silence, formerly SMSSecure)
GPLv3, ~1.1k stars. Fork of TextSecure (now Signal) that keeps SMS encryption.
- SMS/MMS with **Signal-protocol encryption** — no servers or internet required
- All messages encrypted locally (lost/stolen phone protection)
- **Dropped Google services dependencies** — no push, fully self-contained
- Fully open source, auditable
- **Highly relevant**: the strongest Google-free + offline-encrypted model

### Partisan-SMS (wrwrabbit/Partisan-SMS)
GPLv3, ~228 stars. Fork of QKSMS with SMS encryption, built for protesters.
- Encrypted SMS
- Inherits QKSMS feature set
- Niche, less maintained — a reference for the encryption approach, not a base

### Signal (signalapp/Signal-Android)
AGPLv3. Primarily an internet-based messenger now; SMS support has been
deprecated on Android and removed on iOS. Not an SMS-forward app.
- **Design inspiration only**: clean layout, Material You dynamic color,
  minimal chrome. Note: TxxT does **not** adopt Signal's bubble layout — see
  PRIVACY.md §2 (nothing ever bubbles). Not a candidate base for a pure SMS app.

### AOSP Messaging (stock Android Messages)
Apache 2.0. The reference stock SMS/MMS app.
- Reference for core SMS/MMS behavior and the standard Android SMS/MMS
  ContentProvider integration.
- Feature set is deliberately minimal (stock); its value is as a spec reference
  for correct SMS/MMS handling, not a feature source.

## Feature shortlist (compiled from the above + brand)

**Core messaging:** SMS + MMS, group MMS, attachments (any file type),
voice messages, emoji reactions, message pinning, message sorting, archiving,
delayed sending, scheduled messages, quick reply from notifications,
swipe actions.

**Privacy/blocking:** robust blocking (unknown senders + keyword/phrase
filters), blocklist export/import, lock-screen privacy options,
**fully offline (no Internet permission)**.

**Backup:** SMS export/import (and JSON backup, matching the launcher).

**Accessibility:** in-app speech-to-text and text-to-speech.

**Design (brand):** AMOLED black, Space Mono, gesture-driven, minimal/no icon
grid, theme presets (like the launcher's six), text-first surfaces.

## Not-yet-verified / open items
- Private repos `piercingxx-brand`, `nope-mode`, `xx-vitals` — not yet read
  (outside sandbox; needs a read grant). Their design tokens/features will
  refine this list.
- Nagatha cleanroom of the best options — explicitly deferred by operator.