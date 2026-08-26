# TxxT — Design Direction

Design phase for a Google-free, sideloaded SMS/MMS app for a GrapheneOS Pixel 9.

> **Read `INSPIRATION.md` after this file.** It holds the concrete brand-system
> and technical-stack inheritance from the three PiercingXX repos
> (`piercingxx-branding`, `Nope-Mode`, `xx-vitals`), all read this session.
> **Read `PRIVACY.md` too** — it holds the privacy/notification posture that
> governs how every screen behaves.

## Aesthetic direction (defaulted, pending operator confirmation)

**Hybrid.** Keep the operator's brand DNA — AMOLED black, Space Mono,
gesture-driven, text-first, no icon grid — but allow the minimal affordances a
messaging surface actually needs (compose bar, attachment picker). A pure
terminal UI is impractical for SMS/MMS; the hybrid keeps the brand while
remaining usable. **Messages are text-first lines, never chat bubbles** (see
§2 of PRIVACY.md — "nothing ever bubbles").

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
5. **Theme presets.** Replicate the launcher's seven-preset pattern rather than
   a single theme.
6. **Local-first.** Everything the app itself does — search, backup, themes,
   settings, contact-name resolution — happens on the device against local
   storage and local providers. TxxT contacts no service of its own. (Sending
   and receiving a message goes over the carrier network; that is what SMS/MMS
   is, and the app does not pretend otherwise.)
7. **Privacy by default.** Read/typing receipts, MMS smart features, and
   notification bubbles are off; metadata is scrubbed on every send; starred
   contacts always get through. The full posture is `PRIVACY.md` — read it
   after this file.

## Brand system (from `piercingxx-branding`)

TxxT inherits the house brand. Two rules are load-bearing for a messaging UI:

- **Reserved-white.** Pure `#FFFFFF` is the accent alone. Body text tops out at
  90% white. Strong emphasis inverts — a Signal-white block with Ink text.
- **One accent per screen.** Monochrome plus one accent, never two. This deletes
  the conventional messaging palette (blue sent / grey received / red unread);
  hierarchy comes from the white-opacity ramp, not hue.

Concrete mapping for messages (text-first lines, no bubbles — PRIVACY.md §2):
**sent** = `signal` text on black (inverted emphasis); **received** = `slate`/
`graphite` text on black; **unread** = `signal` glyph, not a red dot. Full
tokens and rules in `INSPIRATION.md` §1.

## Screens

### Conversation list
- Text-first rows: contact name (Space Mono), preview line, timestamp.
- No avatar tiles by default (text-first); optional monogram.
- Swipe actions per row.
- Search accessible via gesture/keystroke.

### Conversation thread
- **Text-first message lines on AMOLED black** — no chat bubbles, no rounded
  cards (PRIVACY.md §2 — "nothing ever bubbles"). Inbound left, outbound right;
  minimal chrome.
- Timestamps in Space Mono.
- Compose bar at bottom; attachment affordance minimal. **Shipping:** attach is
  a borderless monospace `⊕` (U+2295) beside the send `➜` (U+279C) — no filled
  pill, no vector icon, and both codepoints are verified present in the bundled
  JetBrains Mono's cmap so they render as type from our own face rather than as
  colour emoji from a system fallback. A staged photo announces itself as a
  **one-line text indicator** above the compose row (`photo · name · size`) with
  a `✕` (U+2715) that removes it without sending — a line of type, not a
  thumbnail card, because this app has no cards. **No voice-message
  affordance** (voice messages are never sent or received — PRIVACY.md §5).
- Quick reply from notification.
- **No notification bubbles / chat-heads** — ever (PRIVACY.md §2).

### Settings
- Theme presets, font toggle, backup/restore, blocking management,
  lock-screen privacy, notification preferences.
- **Starred contacts** — the call-through list that bypasses every suppression
  (PRIVACY.md §6).
- **Theme auto-sync** with the xx-launcher (PRIVACY.md §7).

## Stack (defaulted, pending confirmation)
- **Kotlin** (matches PiercingXX-Launcher).
- **Views + viewBinding** (matches launcher + Nope-Mode; messaging is
  list-heavy, not animation-heavy — unlike xx-vitals' Compose choice).
- **Room + Gson** (matches launcher backup JSON conventions).
- **Pure-Kotlin core** — SMS/MMS state, blocking, backup serialization have no
  `android.*` imports, so they are JVM-testable without a device.
- **Target:** GrapheneOS Pixel 9, sideloaded APK.
- **No Google services** (no Play Services, no Firebase, no push).
- **A justified permission list** — every declared permission carries the
  manifest comment that motivates it, and `scripts/verify_privacy_claims.py`
  fails if the built APK's set drifts from it in either direction (checkable
  with `aapt2 dump permissions`).
- **Gboard compatible** — standard text fields work with any IME, Gboard
  included; no special integration needed.

## Open design questions for the operator
See FEATURES.md — the full list. The pivotal aesthetic question is defaulted
to hybrid above.