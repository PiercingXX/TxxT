# TxxT — Design Direction

Design phase for a Google-free, sideloaded SMS/MMS app for a GrapheneOS Pixel 9.

> **Read `INSPIRATION.md` after this file.** It holds the concrete brand-system
> and technical-stack inheritance from the three PiercingXX repos
> (`piercingxx-branding`, `Nope-Mode`, `xx-vitals`), all read this session.

## Aesthetic direction (defaulted, pending operator confirmation)

**Hybrid.** Keep the operator's brand DNA — AMOLED black, Space Mono,
gesture-driven, text-first, no icon grid — but allow the minimal affordances a
messaging surface actually needs (compose bar, message bubbles, attachment
picker). A pure terminal UI is impractical for SMS/MMS; the hybrid keeps the
brand while remaining usable.

This is the pivotal decision and was defaulted on best judgment because the
operator declined to answer the pivotal design question and asked me to proceed.

## Design principles

1. **Text-first.** Type, whitespace, and alignment carry the interface. No icon
   grids; where an icon is unavoidable it is a minimal glyph, not a filled tile.
2. **AMOLED black.** True `#000000` background for the OLED panel; content
   floats on black. Saves battery and matches the brand.
3. **Monospace.** Space Mono for names/timestamps/unread counts, JetBrains Mono
   for message body. Tabular figures throughout. Fonts shipped in `res/font/`.
4. **Gesture-driven.** Swipe-to-archive, swipe-to-delete, swipe-to-call,
   swipe-to-schedule. Minimize taps.
5. **Theme presets.** Replicate the launcher's six-preset pattern rather than a
   single theme.
6. **Local-first.** No Internet permission. Everything (send, receive, search,
   backup) works offline.

## Brand system (from `piercingxx-branding`)

TxxT inherits the house brand. Two rules are load-bearing for a messaging UI:

- **Reserved-white.** Pure `#FFFFFF` is the accent alone. Body text tops out at
  90% white. Strong emphasis inverts — a Signal-white block with Ink text.
- **One accent per screen.** Monochrome plus one accent, never two. This deletes
  the conventional messaging palette (blue sent / grey received / red unread);
  hierarchy comes from the white-opacity ramp, not hue.

Concrete mapping for messages: **sent** = `signal` block with `ink` text
(inverted emphasis); **received** = `slate`/`graphite` block; **unread** =
`signal` glyph, not a red dot. Full tokens and rules in `INSPIRATION.md` §1.

## Screens

### Conversation list
- Text-first rows: contact name (Space Mono), preview line, timestamp.
- No avatar tiles by default (text-first); optional monogram.
- Swipe actions per row.
- Search accessible via gesture/keystroke.

### Conversation thread
- Message bubbles on AMOLED black; minimal chrome.
- Timestamps in Space Mono.
- Compose bar at bottom; attachment and voice-message affordances minimal.
- Quick reply from notification.

### Settings
- Theme presets, font toggle, backup/restore, blocking management,
  lock-screen privacy, notification preferences.

## Stack (defaulted, pending confirmation)
- **Kotlin** (matches PiercingXX-Launcher).
- **Views + viewBinding** (matches launcher + Nope-Mode; messaging is
  list-heavy, not animation-heavy — unlike xx-vitals' Compose choice).
- **Room + Gson** (matches launcher backup JSON conventions).
- **Pure-Kotlin core** — SMS/MMS state, blocking, backup serialization have no
  `android.*` imports, so they are JVM-testable without a device.
- **Target:** GrapheneOS Pixel 9, sideloaded APK.
- **No Google services** (no Play Services, no Firebase, no push).
- **No `INTERNET` permission** — a verifiable privacy claim (pattern from
  Nope-Mode; checkable with `aapt2 dump permissions`).
- **Gboard compatible** — standard text fields work with any IME, Gboard
  included; no special integration needed.

## Open design questions for the operator
See FEATURES.md — the full list. The pivotal aesthetic question is defaulted
to hybrid above.